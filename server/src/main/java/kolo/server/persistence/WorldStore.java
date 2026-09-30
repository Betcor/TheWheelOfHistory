package kolo.server.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.SaveVersionException;
import kolo.engine.state.WorldState;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

/**
 * Файл світу ({@code *.koloworld}): одна база SQLite на світ, яку можна перенести між режимами гри. Карта пишеться
 * раз, після кожного року — хеш стану й стиснений (gzip) канонічний снапшот; рік пишеться однією транзакцією.
 *
 * <p>Не потокобезпечний: з файлом працює один власник (актор сесії) в одному потоці, і файл має одного записувача.
 * Після {@link #close()} база зведена в один файл (журнал WAL прибирається), тож його можна копіювати.
 */
public final class WorldStore implements AutoCloseable {

    /** Розширення файлу світу. */
    public static final String EXTENSION = ".koloworld";

    /** {@code PRAGMA application_id} файлу світу — «KOLO»: чужа база SQLite не сприймається за світ. */
    static final int APPLICATION_ID = 0x4B4F4C4F;

    private final Path file;
    private final Connection connection;
    private final Clock clock;
    private final WorldMeta meta;
    private final MapSnapshot map;
    private int lastTurn;
    private boolean closed;

    private WorldStore(Path file, Connection connection, Clock clock, WorldMeta meta, MapSnapshot map, int lastTurn) {
        this.file = file;
        this.connection = connection;
        this.clock = clock;
        this.meta = meta;
        this.map = map;
        this.lastTurn = lastTurn;
    }

    /**
     * Створює файл нового світу з картою й початковим станом і відкриває його. Файл з'являється лише цілком: до кінця
     * запису він лежить поруч під тимчасовою назвою.
     *
     * @param name назва світу для списку збережень
     * @throws IllegalArgumentException якщо початковий стан на іншій карті
     * @throws SaveFileException з {@link ErrorCode#SAVE_FILE_ERROR}, якщо файл уже є або запис не вдався
     */
    public static WorldStore create(Path file, String name, MapSnapshot map, StateSnapshot initial) {
        return create(file, name, map, initial, Clock.systemUTC(), Migrator.bundled());
    }

    static WorldStore create(
            Path file, String name, MapSnapshot map, StateSnapshot initial, Clock clock, Migrator migrator) {
        Checks.notBlank("name", name);
        WorldState state = initial.state();
        if (state.map() != map.map() && !state.map().equals(map.map())) {
            throw new IllegalArgumentException("initial state is on another map");
        }
        if (Files.exists(file)) {
            throw SaveErrors.file(file, "create", "file_exists");
        }
        Path temporary = file.resolveSibling("." + file.getFileName() + ".creating");
        try {
            // Залишок попередньої невдалої спроби — наш власний тимчасовий файл.
            deleteDatabase(temporary);
            try (Connection connection = connect(temporary, true)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA application_id = " + APPLICATION_ID);
                }
                migrator.migrate(connection, temporary, clock);
                Instant now = clock.instant();
                connection.setAutoCommit(false);
                insertMeta(connection, new WorldMeta(name, state.seed(), state.contentHash(), map.hash(), now));
                try (PreparedStatement insert =
                        connection.prepareStatement("INSERT INTO world_map (id, map_gz) VALUES (1, ?)")) {
                    insert.setBytes(1, Gzip.compress(map.json()));
                    insert.executeUpdate();
                }
                insertTurn(connection, initial, now);
                connection.commit();
            }
            Files.move(temporary, file);
        } catch (SQLException e) {
            deleteQuietly(temporary);
            throw SaveErrors.sql(file, "create", e);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw Files.exists(file)
                    ? SaveErrors.file(file, "create", "file_exists")
                    : SaveErrors.io(file, "create", e);
        } catch (RuntimeException e) {
            deleteQuietly(temporary);
            throw e;
        }
        return open(file, clock, migrator);
    }

    /**
     * Відкриває файл світу; файл зі старішою схемою мігрується (з резервною копією поруч).
     *
     * @throws SaveVersionException якщо файл створено новішою версією гри
     * @throws SaveFileException з {@link ErrorCode#SAVE_FILE_ERROR}, якщо файлу немає або SQLite не відкриває його;
     *     з {@link ErrorCode#SAVE_MALFORMED}, якщо це не файл світу або його вміст пошкоджено
     */
    public static WorldStore open(Path file) {
        return open(file, Clock.systemUTC(), Migrator.bundled());
    }

    static WorldStore open(Path file, Clock clock, Migrator migrator) {
        if (!Files.isRegularFile(file)) {
            throw SaveErrors.file(file, "open", "file_missing");
        }
        Connection connection;
        try {
            connection = connect(file, false);
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "open", e);
        }
        try {
            if (applicationId(connection) != APPLICATION_ID) {
                throw SaveErrors.notWorldFile(file, null);
            }
            migrator.migrate(connection, file, clock);
            WorldMeta meta = readMeta(connection);
            MapSnapshot map = readMap(connection, meta);
            int lastTurn = lastTurn(connection);
            return new WorldStore(file, connection, clock, meta, map, lastTurn);
        } catch (SQLException e) {
            closeQuietly(connection);
            throw SaveErrors.sql(file, "open", e);
        } catch (RuntimeException e) {
            closeQuietly(connection);
            throw e;
        }
    }

    public Path file() {
        return file;
    }

    public WorldMeta meta() {
        return meta;
    }

    /** Карта світу; читається раз при відкритті. */
    public MapSnapshot map() {
        return map;
    }

    /** Останній збережений рік. */
    public int lastTurn() {
        return lastTurn;
    }

    /**
     * Зберігає стан наприкінці року: рядок року з хешем і снапшот — однією транзакцією.
     *
     * @throws IllegalArgumentException якщо стан іншого світу або рік не пізніший за останній збережений — помилка
     *     виклику, а не даних
     * @throws SaveFileException з {@link ErrorCode#SAVE_FILE_ERROR}, якщо запис не вдався; файл лишається на
     *     попередньому році
     */
    public void saveTurn(StateSnapshot snapshot) {
        ensureOpen();
        WorldState state = snapshot.state();
        if (state.seed() != meta.seed()
                || !state.contentHash().equals(meta.contentHash())
                || (state.map() != map.map() && !state.map().equals(map.map()))) {
            throw new IllegalArgumentException("snapshot is of another world");
        }
        if (state.turn() <= lastTurn) {
            throw new IllegalArgumentException("turn " + state.turn() + " is not after saved turn " + lastTurn);
        }
        try {
            connection.setAutoCommit(false);
            try {
                insertTurn(connection, snapshot, clock.instant());
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "save_turn", e);
        }
        lastTurn = state.turn();
    }

    /** Збережені роки за зростанням. */
    public List<SavedTurn> turns() {
        ensureOpen();
        List<SavedTurn> turns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT t.turn, t.state_hash, t.saved_at, s.turn IS NOT NULL"
                        + " FROM turns t LEFT JOIN snapshots s ON s.turn = t.turn ORDER BY t.turn")) {
            while (rows.next()) {
                int turn = rows.getInt(1);
                turns.add(new SavedTurn(
                        turn,
                        rows.getString(2),
                        instant(rows.getString(3), "turns[" + turn + "]"),
                        rows.getBoolean(4)));
            }
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "load", e);
        }
        return List.copyOf(turns);
    }

    /**
     * Стан наприкінці року; порожньо, якщо снапшота цього року немає.
     *
     * @throws SaveFileException з {@link ErrorCode#SAVE_MALFORMED}, якщо снапшот пошкоджено або не збігається з
     *     хешем року
     */
    public Optional<StateSnapshot> loadState(int turn) {
        ensureOpen();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT t.state_hash, s.state_gz FROM snapshots s JOIN turns t ON t.turn = s.turn WHERE s.turn = ?")) {
            select.setInt(1, turn);
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(readState(turn, row.getString(1), row.getBytes(2)));
            }
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "load", e);
        }
    }

    /** Стан з останнього снапшота — з нього продовжується гра. */
    public StateSnapshot loadLatest() {
        ensureOpen();
        int turn;
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT MAX(turn) FROM snapshots")) {
            row.next();
            turn = row.getInt(1);
            if (row.wasNull()) {
                throw SaveErrors.malformed("file", "snapshots", "no_snapshots");
            }
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "load", e);
        }
        return loadState(turn).orElseThrow();
    }

    /** Закриває файл; повторний виклик нічого не робить. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            connection.close();
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "close", e);
        }
    }

    @Override
    public String toString() {
        return "WorldStore[" + file.getFileName() + ", turn " + lastTurn + "]";
    }

    // ---- Читання ----

    private StateSnapshot readState(int turn, String hash, byte[] gz) {
        String location = "snapshots[" + turn + "]";
        byte[] json = Gzip.decompress(gz, "state", location);
        if (!Sha256.hex(json).equals(hash)) {
            throw SaveErrors.malformed("state", location, "state_hash_mismatch");
        }
        StateSnapshot snapshot = StateSnapshot.read(json, map);
        WorldState state = snapshot.state();
        if (state.turn() != turn) {
            throw SaveErrors.malformed("state", location, "turn_mismatch");
        }
        if (state.seed() != meta.seed() || !state.contentHash().equals(meta.contentHash())) {
            throw SaveErrors.malformed("state", location, "world_mismatch");
        }
        return snapshot;
    }

    private static WorldMeta readMeta(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(
                        "SELECT name, seed, content_hash, map_hash, created_at FROM world_meta WHERE id = 1")) {
            if (!row.next()) {
                throw SaveErrors.malformed("file", "world_meta", "missing_row");
            }
            return new WorldMeta(
                    row.getString(1),
                    row.getLong(2),
                    row.getString(3),
                    row.getString(4),
                    instant(row.getString(5), "world_meta"));
        }
    }

    private static MapSnapshot readMap(Connection connection, WorldMeta meta) throws SQLException {
        byte[] gz;
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT map_gz FROM world_map WHERE id = 1")) {
            if (!row.next()) {
                throw SaveErrors.malformed("file", "world_map", "missing_row");
            }
            gz = row.getBytes(1);
        }
        MapSnapshot map = MapSnapshot.read(Gzip.decompress(gz, "map", "world_map"));
        if (!map.hash().equals(meta.mapHash())) {
            throw SaveErrors.malformed("map", "world_map", "map_hash_mismatch");
        }
        return map;
    }

    private static int lastTurn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT MAX(turn) FROM turns")) {
            row.next();
            int turn = row.getInt(1);
            if (row.wasNull()) {
                throw SaveErrors.malformed("file", "turns", "no_turns");
            }
            return turn;
        }
    }

    private static int applicationId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("PRAGMA application_id")) {
            row.next();
            return row.getInt(1);
        }
    }

    private static Instant instant(String text, String location) {
        if (text == null) {
            throw SaveErrors.malformed("file", location, "bad_timestamp");
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw SaveErrors.malformed("file", location, "bad_timestamp", e);
        }
    }

    // ---- Запис ----

    private static void insertMeta(Connection connection, WorldMeta meta) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO world_meta"
                + " (id, name, seed, content_hash, map_hash, created_at) VALUES (1, ?, ?, ?, ?, ?)")) {
            insert.setString(1, meta.name());
            insert.setLong(2, meta.seed());
            insert.setString(3, meta.contentHash());
            insert.setString(4, meta.mapHash());
            insert.setString(5, meta.createdAt().toString());
            insert.executeUpdate();
        }
    }

    private static void insertTurn(Connection connection, StateSnapshot snapshot, Instant savedAt) throws SQLException {
        int turn = snapshot.state().turn();
        try (PreparedStatement insert =
                connection.prepareStatement("INSERT INTO turns (turn, state_hash, saved_at) VALUES (?, ?, ?)")) {
            insert.setInt(1, turn);
            insert.setString(2, snapshot.hash());
            insert.setString(3, savedAt.toString());
            insert.executeUpdate();
        }
        try (PreparedStatement insert =
                connection.prepareStatement("INSERT INTO snapshots (turn, state_gz) VALUES (?, ?)")) {
            insert.setInt(1, turn);
            insert.setBytes(2, Gzip.compress(snapshot.json()));
            insert.executeUpdate();
        }
    }

    // ---- З'єднання й файли ----

    private static Connection connect(Path file, boolean create) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setOpenMode(SQLiteOpenMode.READWRITE);
        if (create) {
            config.setOpenMode(SQLiteOpenMode.CREATE);
        } else {
            config.resetOpenMode(SQLiteOpenMode.CREATE);
        }
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        return config.createConnection("jdbc:sqlite:" + file.toAbsolutePath());
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("world store is closed");
        }
    }

    /** Файл бази разом із журналом WAL і спільною пам'яттю. */
    private static void deleteDatabase(Path file) throws IOException {
        for (String suffix : List.of("", "-wal", "-shm")) {
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + suffix));
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            deleteDatabase(file);
        } catch (IOException e) {
            // Тимчасовий файл лишиться; наступна спроба створення прибере його сама.
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException e) {
            // Первинна помилка важливіша за помилку закриття.
        }
    }
}
