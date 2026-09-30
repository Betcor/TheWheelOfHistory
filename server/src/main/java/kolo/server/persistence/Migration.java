package kolo.server.persistence;

import java.util.List;
import java.util.Objects;

/**
 * Міграція схеми файлу світу: SQL-скрипт з номером версії. Версія схеми файлу — окрема від версії схеми снапшота
 * ({@code WorldState.SCHEMA_VERSION}): таблиці й стан змінюються незалежно.
 */
record Migration(int version, String name, List<String> statements) {

    Migration {
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive: " + version);
        }
        Objects.requireNonNull(name, "name");
        statements = List.copyOf(statements);
        if (statements.isEmpty()) {
            throw new IllegalArgumentException("migration " + name + " is empty");
        }
    }

    /** Міграція з файлу {@code NNNN_назва.sql}: номер — версія. */
    static Migration of(String fileName, String script) {
        int separator = fileName.indexOf('_');
        if (separator < 1 || !fileName.endsWith(".sql")) {
            throw new IllegalArgumentException("migration file must be NNNN_name.sql: " + fileName);
        }
        return new Migration(
                Integer.parseInt(fileName.substring(0, separator)),
                fileName.substring(separator + 1, fileName.length() - ".sql".length()),
                SqlScript.statements(script));
    }
}
