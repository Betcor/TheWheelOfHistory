package kolo.server.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Файли світу в тестах: сталий годинник і правка бази в обхід {@link WorldStore}, щоб зіпсувати файл. */
final class TestStores {

    static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private TestStores() {}

    static WorldStore create(Path file, StateSnapshot initial, MapSnapshot map) {
        return WorldStore.create(file, "Тестовий світ", map, initial, CLOCK, Migrator.bundled());
    }

    static WorldStore open(Path file) {
        return WorldStore.open(file, CLOCK, Migrator.bundled());
    }

    /** Виконує оператор над закритим файлом світу; параметри — рядки, числа або байти. */
    static void sql(Path file, String sql, Object... parameters) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.execute();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
