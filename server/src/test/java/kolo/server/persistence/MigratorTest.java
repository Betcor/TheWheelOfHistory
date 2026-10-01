package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import kolo.engine.error.SaveVersionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigratorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private static final Migration FIRST = Migration.of("0001_first.sql", "CREATE TABLE a (x INTEGER);");
    private static final Migration SECOND =
            Migration.of("0002_second.sql", "ALTER TABLE a ADD COLUMN y TEXT;\nCREATE TABLE b (z INTEGER);");

    @TempDir
    Path dir;

    @Test
    void bundledSchemaHasWorldTables() throws SQLException {
        Path file = dir.resolve("w.db");
        try (Connection connection = connect(file)) {
            Migrator migrator = Migrator.bundled();

            assertThat(migrator.migrate(connection, file, CLOCK)).containsExactly(1, 2, 3);
            assertThat(tables(connection))
                    .containsExactly("players", "schema_migrations", "snapshots", "turns", "world_map", "world_meta");
            assertThat(migrator.version(connection)).isEqualTo(migrator.latest());
        }
    }

    @Test
    void emptyDatabaseGetsAllMigrationsWithoutBackup() throws SQLException {
        Path file = dir.resolve("w.db");
        try (Connection connection = connect(file)) {
            assertThat(new Migrator(List.of(FIRST, SECOND)).migrate(connection, file, CLOCK))
                    .containsExactly(1, 2);
            assertThat(query(connection, "SELECT version || ':' || name || ':' || applied_at FROM schema_migrations"))
                    .containsExactly("1:first:2026-10-01T12:00:00Z", "2:second:2026-10-01T12:00:00Z");
        }
        assertThat(backups()).isEmpty();
    }

    @Test
    void upToDateDatabaseIsLeftAlone() throws SQLException {
        Path file = dir.resolve("w.db");
        try (Connection connection = connect(file)) {
            Migrator migrator = new Migrator(List.of(FIRST));
            migrator.migrate(connection, file, CLOCK);

            assertThat(migrator.migrate(connection, file, CLOCK)).isEmpty();
        }
        assertThat(backups()).isEmpty();
    }

    @Test
    void olderDatabaseIsBackedUpThenMigrated() throws SQLException {
        Path file = dir.resolve("w.db");
        try (Connection connection = connect(file)) {
            new Migrator(List.of(FIRST)).migrate(connection, file, CLOCK);
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO a (x) VALUES (7)");
            }

            assertThat(new Migrator(List.of(FIRST, SECOND)).migrate(connection, file, CLOCK))
                    .containsExactly(2);
            assertThat(tables(connection)).contains("b");
        }
        Path backup = dir.resolve("w.db.v1.bak");
        assertThat(backups()).containsExactly(backup);
        try (Connection old = connect(backup)) {
            assertThat(new Migrator(List.of(FIRST)).version(old)).isEqualTo(1);
            assertThat(query(old, "SELECT x FROM a")).containsExactly("7");
            assertThat(tables(old)).doesNotContain("b");
        }
    }

    @Test
    void existingBackupIsNotOverwritten() throws Exception {
        Path file = dir.resolve("w.db");
        Files.writeString(dir.resolve("w.db.v1.bak"), "попередня копія");

        assertThat(Migrator.backupPath(file, 1)).isEqualTo(dir.resolve("w.db.v1-1.bak"));
        assertThat(Migrator.backupPath(file, 2)).isEqualTo(dir.resolve("w.db.v2.bak"));
    }

    @Test
    void newerDatabaseIsRejected() throws SQLException {
        Path file = dir.resolve("w.db");
        try (Connection connection = connect(file)) {
            new Migrator(List.of(FIRST, SECOND)).migrate(connection, file, CLOCK);

            assertThatThrownBy(() -> new Migrator(List.of(FIRST)).migrate(connection, file, CLOCK))
                    .isInstanceOfSatisfying(
                            SaveVersionException.class,
                            e -> assertThat(e.details())
                                    .containsEntry("part", "file")
                                    .containsEntry("version", 2)
                                    .containsEntry("supported", 1));
        }
    }

    @Test
    void failedMigrationLeavesPreviousVersion() throws SQLException {
        Path file = dir.resolve("w.db");
        Migration broken = Migration.of("0002_broken.sql", "CREATE TABLE b (z INTEGER);\nCREATE TABLE a (x INTEGER);");
        try (Connection connection = connect(file)) {
            new Migrator(List.of(FIRST)).migrate(connection, file, CLOCK);
            Migrator migrator = new Migrator(List.of(FIRST, broken));

            assertThatThrownBy(() -> migrator.migrate(connection, file, CLOCK)).isInstanceOf(SQLException.class);
            assertThat(migrator.version(connection)).isEqualTo(1);
            assertThat(tables(connection)).doesNotContain("b");
            assertThat(connection.getAutoCommit()).isTrue();
        }
    }

    @Test
    void versionsMustBeConsecutive() {
        assertThatThrownBy(() -> new Migrator(List.of(SECOND))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Migrator(List.of(FIRST, FIRST))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void migrationFileNameGivesVersionAndName() {
        assertThat(FIRST.version()).isEqualTo(1);
        assertThat(FIRST.name()).isEqualTo("first");
        assertThatThrownBy(() -> Migration.of("first.sql", "SELECT 1;")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Migration.of("0003_empty.sql", "-- нічого"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<Path> backups() {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".bak"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Connection connect(Path file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file);
    }

    private static List<String> tables(Connection connection) throws SQLException {
        return query(connection, "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name");
    }

    private static List<String> query(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
