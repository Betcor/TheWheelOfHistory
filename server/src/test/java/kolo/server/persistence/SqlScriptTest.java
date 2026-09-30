package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlScriptTest {

    @Test
    void splitsStatementsAtSemicolonsEndingLines() {
        String script = """
                -- заголовок
                CREATE TABLE a (
                    x INTEGER, -- коментар у рядку
                    y TEXT
                );

                CREATE TABLE b (z INTEGER);
                """;

        assertThat(SqlScript.statements(script))
                .containsExactly("CREATE TABLE a (\nx INTEGER,\ny TEXT\n)", "CREATE TABLE b (z INTEGER)");
    }

    @Test
    void lastStatementMayLackSemicolon() {
        assertThat(SqlScript.statements("SELECT 1;\r\nSELECT 2")).containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    void emptyScriptHasNoStatements() {
        assertThat(SqlScript.statements("-- лише коментар\n\n;\n")).isEmpty();
    }
}
