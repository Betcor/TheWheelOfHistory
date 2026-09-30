package kolo.server.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Розбір SQL-скрипту міграції на оператори: драйвер SQLite виконує за виклик лише перший оператор. Правила навмисно
 * прості й достатні для схеми: коментар — від {@code --} до кінця рядка, оператор закінчується {@code ;} у кінці
 * рядка. Рядкових літералів з {@code --} чи {@code ;}, тригерів і {@code BEGIN … END} у скриптах немає.
 */
final class SqlScript {

    private SqlScript() {}

    static List<String> statements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R", -1)) {
            int comment = line.indexOf("--");
            String code = (comment < 0 ? line : line.substring(0, comment)).strip();
            if (code.isEmpty()) {
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            if (code.endsWith(";")) {
                current.append(code, 0, code.length() - 1);
                add(statements, current);
            } else {
                current.append(code);
            }
        }
        add(statements, current);
        return List.copyOf(statements);
    }

    private static void add(List<String> statements, StringBuilder current) {
        String statement = current.toString().strip();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }
}
