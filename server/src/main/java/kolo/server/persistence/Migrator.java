package kolo.server.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.SaveVersionException;

/**
 * Власний мігратор схеми файлу світу: застосовує SQL-скрипти з ресурсів {@code migrations/} по черзі, кожен — однією
 * транзакцією разом із записом у {@code schema_migrations}. Перед міграцією вже існуючого файлу робиться резервна
 * копія: невдалий перехід не мусить коштувати гравцеві світу.
 */
final class Migrator {

    /** Скрипти в порядку версій; новий скрипт — лише в кінець, старі не змінюються. */
    private static final List<String> BUNDLED_SCRIPTS = List.of("0001_world.sql");

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS schema_migrations ("
            + "version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT NOT NULL)";

    private final List<Migration> migrations;

    /** @param migrations версії 1, 2, … без пропусків */
    Migrator(List<Migration> migrations) {
        this.migrations = List.copyOf(migrations);
        for (int i = 0; i < this.migrations.size(); i++) {
            if (this.migrations.get(i).version() != i + 1) {
                throw new IllegalArgumentException("migration versions must be 1, 2, …: " + this.migrations);
            }
        }
    }

    /** Мігратор зі скриптами гри. */
    static Migrator bundled() {
        List<Migration> migrations = new ArrayList<>();
        for (String script : BUNDLED_SCRIPTS) {
            migrations.add(Migration.of(script, resource(script)));
        }
        return new Migrator(migrations);
    }

    /** Версія схеми, яку знає гра. */
    int latest() {
        return migrations.size();
    }

    /** Версія схеми файлу; 0 — порожня база без таблиць гри. */
    int version(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            try (ResultSet max = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
                max.next();
                return max.getInt(1);
            }
        }
    }

    /**
     * Доводить схему файлу до останньої версії.
     *
     * @param file файл бази — для резервної копії поруч
     * @return застосовані версії
     * @throws SaveVersionException якщо файл створено новішою версією гри
     */
    List<Integer> migrate(Connection connection, Path file, Clock clock) throws SQLException {
        int version = version(connection);
        if (version > latest()) {
            throw new SaveVersionException(ErrorDetails.of("part", "file", "version", version, "supported", latest()));
        }
        if (version == latest()) {
            return List.of();
        }
        if (version > 0) {
            backup(connection, file, version);
        }
        List<Integer> applied = new ArrayList<>();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (Migration migration : migrations.subList(version, latest())) {
                apply(connection, migration, clock);
                connection.commit();
                applied.add(migration.version());
            }
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        return List.copyOf(applied);
    }

    private static void apply(Connection connection, Migration migration, Clock clock) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : migration.statements()) {
                statement.execute(sql);
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO schema_migrations (version, name, applied_at) VALUES (?, ?, ?)")) {
            insert.setInt(1, migration.version());
            insert.setString(2, migration.name());
            insert.setString(3, clock.instant().toString());
            insert.executeUpdate();
        }
    }

    /** Копія файлу до міграції: {@code світ.koloworld.v1.bak}; наявні копії не перезаписуються. */
    private static void backup(Connection connection, Path file, int version) throws SQLException {
        Path backup = backupPath(file, version);
        // VACUUM INTO пише цілісну копію бази, навіть у режимі WAL, без копіювання файлів поза SQLite.
        try (PreparedStatement vacuum = connection.prepareStatement("VACUUM INTO ?")) {
            vacuum.setString(1, backup.toString());
            vacuum.execute();
        }
    }

    static Path backupPath(Path file, int version) {
        String base = file.getFileName() + ".v" + version;
        Path backup = file.resolveSibling(base + ".bak");
        for (int n = 1; Files.exists(backup); n++) {
            backup = file.resolveSibling(base + "-" + n + ".bak");
        }
        return backup;
    }

    private static String resource(String script) {
        try (InputStream in = Migrator.class.getResourceAsStream("migrations/" + script)) {
            if (in == null) {
                throw new IllegalStateException("missing migration resource " + script);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
