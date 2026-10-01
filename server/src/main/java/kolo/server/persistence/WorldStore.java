package kolo.server.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.SaveVersionException;
import kolo.engine.error.ValidationException;
import kolo.engine.state.ControlType;
import kolo.engine.state.Country;
import kolo.engine.state.CountryId;
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

    /** Довжина ключа світу, байтів. */
    private static final int KEY_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

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
        return create(file, name, map, initial, List.of());
    }

    /**
     * Те саме з гравцями світу — вони з'являються у файлі разом зі світом.
     *
     * @throws IllegalArgumentException якщо номери чи держави гравців повторюються, держава не гравця або хостів
     *     більше одного
     */
    public static WorldStore create(
            Path file, String name, MapSnapshot map, StateSnapshot initial, List<PlayerRecord> players) {
        return create(file, name, newKey(), map, initial, players);
    }

    /**
     * Те саме із заданим ключем світу: сесія називає його гравцям ще в лобі, до створення файлу.
     *
     * @param key ключ світу ({@link #newKey()})
     */
    public static WorldStore create(
            Path file, String name, String key, MapSnapshot map, StateSnapshot initial, List<PlayerRecord> players) {
        return create(file, name, key, map, initial, players, Clock.systemUTC(), Migrator.bundled());
    }

    static WorldStore create(
            Path file, String name, MapSnapshot map, StateSnapshot initial, Clock clock, Migrator migrator) {
        return create(file, name, map, initial, List.of(), clock, migrator);
    }

    static WorldStore create(
            Path file,
            String name,
            MapSnapshot map,
            StateSnapshot initial,
            List<PlayerRecord> players,
            Clock clock,
            Migrator migrator) {
        return create(file, name, newKey(), map, initial, players, clock, migrator);
    }

    static WorldStore create(
            Path file,
            String name,
            String key,
            MapSnapshot map,
            StateSnapshot initial,
            List<PlayerRecord> players,
            Clock clock,
            Migrator migrator) {
        Checks.notBlank("name", name);
        Checks.notBlank("key", key);
        WorldState state = initial.state();
        if (state.map() != map.map() && !state.map().equals(map.map())) {
            throw new IllegalArgumentException("initial state is on another map");
        }
        checkPlayers(state, players);
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
                insertMeta(connection, new WorldMeta(name, key, state.seed(), state.contentHash(), map.hash(), now));
                try (PreparedStatement insert =
                        connection.prepareStatement("INSERT INTO world_map (id, map_gz) VALUES (1, ?)")) {
                    insert.setBytes(1, Gzip.compress(map.json()));
                    insert.executeUpdate();
                }
                insertTurn(connection, initial, now);
                for (PlayerRecord player : players) {
                    insertPlayer(connection, player, now);
                }
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

    /**
     * Короткий опис файлу світу для списку збережень. Файл лише читається й не мігрується: перелік збережень не мусить
     * змінювати файли. Файл старішої схеми описується тим, що в ньому вже є.
     *
     * @throws SaveVersionException якщо файл створено новішою версією гри
     * @throws SaveFileException з {@link ErrorCode#SAVE_FILE_ERROR}, якщо файлу немає або SQLite не відкриває його;
     *     з {@link ErrorCode#SAVE_MALFORMED}, якщо це не файл світу або його вміст пошкоджено
     */
    public static WorldSummary summary(Path file) {
        return summary(file, Migrator.bundled());
    }

    static WorldSummary summary(Path file, Migrator migrator) {
        if (!Files.isRegularFile(file)) {
            throw SaveErrors.file(file, "open", "file_missing");
        }
        try (Connection connection = connectForReading(file)) {
            if (applicationId(connection) != APPLICATION_ID) {
                throw SaveErrors.notWorldFile(file, null);
            }
            int version = schemaVersion(connection);
            if (version > migrator.latest()) {
                throw new SaveVersionException(
                        ErrorDetails.of("part", "file", "version", version, "supported", migrator.latest()));
            }
            Optional<String> key = Optional.empty();
            String select = "SELECT seed, content_hash";
            if (hasColumn(connection, "world_meta", "world_key")) {
                select += ", world_key";
            }
            long seed;
            String contentHash;
            try (Statement statement = connection.createStatement();
                    ResultSet row = statement.executeQuery(select + " FROM world_meta WHERE id = 1")) {
                if (!row.next()) {
                    throw SaveErrors.malformed("file", "world_meta", "missing_row");
                }
                seed = row.getLong(1);
                contentHash = row.getString(2);
                if (row.getMetaData().getColumnCount() == 3) {
                    key = Optional.ofNullable(row.getString(3)).filter(value -> !value.isBlank());
                }
            }
            if (contentHash == null) {
                throw SaveErrors.malformed("file", "world_meta", "missing_content_hash");
            }
            int lastTurn = lastTurn(connection);
            Instant savedAt;
            try (PreparedStatement savedTurn =
                    connection.prepareStatement("SELECT saved_at FROM turns WHERE turn = ?")) {
                savedTurn.setInt(1, lastTurn);
                try (ResultSet row = savedTurn.executeQuery()) {
                    row.next();
                    savedAt = instant(row.getString(1), "turns[" + lastTurn + "]");
                }
            }
            List<String> players = new ArrayList<>();
            if (hasTable(connection, "players")) {
                try (Statement statement = connection.createStatement();
                        ResultSet rows = statement.executeQuery("SELECT nickname FROM players ORDER BY id")) {
                    while (rows.next()) {
                        String nickname = rows.getString(1);
                        if (nickname == null) {
                            throw SaveErrors.malformed("file", "players", "bad_player");
                        }
                        players.add(nickname);
                    }
                }
            }
            return new WorldSummary(file, key, seed, contentHash, lastTurn, savedAt, players);
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "open", e);
        }
    }

    /** Новий випадковий ключ світу: 16 байтів {@link SecureRandom} у hex. */
    public static String newKey() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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

    /**
     * Переписує гравців світу, що вже є у файлі: нікнейм, хеш токена й хоста (гравцеві віддали вільне місце, світ
     * продовжує інший хост). Номери й держави не змінюються. Однією транзакцією.
     *
     * @throws IllegalArgumentException якщо номер чи держава гравця не такі, як у файлі, або хостів більше одного
     * @throws SaveFileException з {@link ErrorCode#SAVE_FILE_ERROR}, якщо запис не вдався
     */
    public void updatePlayers(List<PlayerRecord> players) {
        ensureOpen();
        TreeSet<Integer> numbers = new TreeSet<>();
        int hosts = 0;
        for (PlayerRecord player : players) {
            if (!numbers.add(player.number())) {
                throw new IllegalArgumentException("players repeat a number: " + players);
            }
            hosts += player.host() ? 1 : 0;
        }
        if (hosts > 1) {
            throw new IllegalArgumentException("more than one host: " + players);
        }
        try {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement clear = connection.prepareStatement("UPDATE players SET is_host = 0")) {
                    clear.executeUpdate();
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE players SET nickname = ?,"
                        + " token_hash = ?, is_host = ? WHERE id = ? AND country = ?")) {
                    for (PlayerRecord player : players) {
                        update.setString(1, player.nickname());
                        update.setString(2, player.tokenHash());
                        update.setInt(3, player.host() ? 1 : 0);
                        update.setInt(4, player.number());
                        update.setInt(5, player.country());
                        if (update.executeUpdate() != 1) {
                            throw new IllegalArgumentException(
                                    "no player " + player.number() + " with country " + player.country());
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "save_player", e);
        }
    }

    /** Гравці світу за номером. */
    public List<SavedPlayer> players() {
        ensureOpen();
        List<SavedPlayer> players = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, nickname, token_hash, country, is_host,"
                        + " last_seen_at FROM players ORDER BY id")) {
            while (rows.next()) {
                int number = rows.getInt(1);
                String location = "players[" + number + "]";
                PlayerRecord player;
                try {
                    player = new PlayerRecord(
                            number, rows.getString(2), rows.getString(3), rows.getInt(4), rows.getInt(5) == 1);
                } catch (ValidationException | NullPointerException e) {
                    throw SaveErrors.malformed("file", location, "bad_player", e);
                }
                players.add(new SavedPlayer(player, instant(rows.getString(6), location)));
            }
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "load", e);
        }
        return List.copyOf(players);
    }

    /**
     * Позначає, що гравець щойно був на зв'язку.
     *
     * @throws IllegalArgumentException якщо такого гравця у світі немає
     * @throws SaveFileException з {@link ErrorCode#SAVE_FILE_ERROR}, якщо запис не вдався
     */
    public void markSeen(int player) {
        ensureOpen();
        int updated;
        try (PreparedStatement update =
                connection.prepareStatement("UPDATE players SET last_seen_at = ? WHERE id = ?")) {
            update.setString(1, clock.instant().toString());
            update.setInt(2, player);
            updated = update.executeUpdate();
        } catch (SQLException e) {
            throw SaveErrors.sql(file, "save_player", e);
        }
        if (updated == 0) {
            throw new IllegalArgumentException("no player " + player);
        }
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
                        "SELECT name, world_key, seed, content_hash, map_hash, created_at FROM world_meta WHERE id = 1")) {
            if (!row.next()) {
                throw SaveErrors.malformed("file", "world_meta", "missing_row");
            }
            try {
                return new WorldMeta(
                        row.getString(1),
                        row.getString(2),
                        row.getLong(3),
                        row.getString(4),
                        row.getString(5),
                        instant(row.getString(6), "world_meta"));
            } catch (ValidationException | NullPointerException e) {
                throw SaveErrors.malformed("file", "world_meta", "bad_meta", e);
            }
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

    private static int schemaVersion(Connection connection) throws SQLException {
        if (!hasTable(connection, "schema_migrations")) {
            return 0;
        }
        try (Statement statement = connection.createStatement();
                ResultSet max = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
            max.next();
            return max.getInt(1);
        }
    }

    private static boolean hasTable(Connection connection, String table) throws SQLException {
        try (PreparedStatement select =
                connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            select.setString(1, table);
            try (ResultSet row = select.executeQuery()) {
                return row.next();
            }
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement select =
                connection.prepareStatement("SELECT 1 FROM pragma_table_info(?) WHERE name = ?")) {
            select.setString(1, table);
            select.setString(2, column);
            try (ResultSet row = select.executeQuery()) {
                return row.next();
            }
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
                + " (id, name, world_key, seed, content_hash, map_hash, created_at) VALUES (1, ?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, meta.name());
            insert.setString(2, meta.key());
            insert.setLong(3, meta.seed());
            insert.setString(4, meta.contentHash());
            insert.setString(5, meta.mapHash());
            insert.setString(6, meta.createdAt().toString());
            insert.executeUpdate();
        }
    }

    private static void checkPlayers(WorldState state, List<PlayerRecord> players) {
        TreeSet<Integer> numbers = new TreeSet<>();
        TreeSet<Integer> countries = new TreeSet<>();
        int hosts = 0;
        for (PlayerRecord player : players) {
            if (!numbers.add(player.number()) || !countries.add(player.country())) {
                throw new IllegalArgumentException("players repeat a number or a country: " + players);
            }
            Country country = state.countries().get(CountryId.of(player.country()));
            if (country == null || country.control() != ControlType.PLAYER) {
                throw new IllegalArgumentException("country " + player.country() + " is not a player country");
            }
            hosts += player.host() ? 1 : 0;
        }
        if (hosts > 1) {
            throw new IllegalArgumentException("more than one host: " + players);
        }
    }

    private static void insertPlayer(Connection connection, PlayerRecord player, Instant now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO players"
                + " (id, nickname, token_hash, country, is_host, last_seen_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setInt(1, player.number());
            insert.setString(2, player.nickname());
            insert.setString(3, player.tokenHash());
            insert.setInt(4, player.country());
            insert.setInt(5, player.host() ? 1 : 0);
            insert.setString(6, now.toString());
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

    /**
     * Для огляду: файл не створюється, а запити лише читають ({@code query_only}). Не режим «лише читання» SQLite: у
     * ньому з'єднання з базою WAL лишає поруч порожні {@code -wal} і {@code -shm}, бо не може прибрати їх при закритті.
     */
    private static Connection connectForReading(Path file) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setOpenMode(SQLiteOpenMode.READWRITE);
        config.resetOpenMode(SQLiteOpenMode.CREATE);
        Connection connection = config.createConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only = 1");
        } catch (SQLException e) {
            closeQuietly(connection);
            throw e;
        }
        return connection;
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
