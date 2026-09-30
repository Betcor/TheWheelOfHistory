package kolo.server.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.SaveFileException;

/** Помилки файлу світу з подробицями для гравця й розробника. Шлях — лише ім'я файлу: каталоги гравця не потрібні. */
final class SaveErrors {

    /** Код SQLite «файл — не база даних». */
    private static final int SQLITE_NOTADB = 26;

    private SaveErrors() {}

    /** Операцію з файлом не виконано з причини, відомої заздалегідь ({@code file_exists}, {@code file_missing}). */
    static SaveFileException file(Path file, String operation, String problem) {
        return new SaveFileException(
                ErrorCode.SAVE_FILE_ERROR,
                ErrorDetails.of("file", name(file), "operation", operation, "problem", problem));
    }

    /** Помилка вводу-виводу файлової системи (перейменування, видалення). */
    static SaveFileException io(Path file, String operation, IOException e) {
        return new SaveFileException(
                ErrorCode.SAVE_FILE_ERROR,
                ErrorDetails.of("file", name(file), "operation", operation, "problem", "io_error"),
                e);
    }

    /** Помилка SQLite; файл, що не є базою даних, — не збій, а чужий файл. */
    static SaveFileException sql(Path file, String operation, SQLException e) {
        if (e.getErrorCode() == SQLITE_NOTADB) {
            return notWorldFile(file, e);
        }
        return new SaveFileException(
                ErrorCode.SAVE_FILE_ERROR,
                ErrorDetails.of("file", name(file), "operation", operation, "sqlite_code", e.getErrorCode()),
                e);
    }

    /** Файл не є файлом світу: інший формат або чужа база SQLite. */
    static SaveFileException notWorldFile(Path file, Throwable cause) {
        return new SaveFileException(
                ErrorCode.SAVE_MALFORMED,
                ErrorDetails.of("part", "file", "location", name(file), "problem", "not_a_world_file"),
                cause);
    }

    /** Вміст файлу світу суперечливий: {@code location} — таблиця й рядок ({@code snapshots[3]}). */
    static SaveFileException malformed(String part, String location, String problem) {
        return new SaveFileException(
                ErrorCode.SAVE_MALFORMED, ErrorDetails.of("part", part, "location", location, "problem", problem));
    }

    static SaveFileException malformed(String part, String location, String problem, Throwable cause) {
        return new SaveFileException(
                ErrorCode.SAVE_MALFORMED,
                ErrorDetails.of("part", part, "location", location, "problem", problem),
                cause);
    }

    private static String name(Path file) {
        Path name = file.getFileName();
        return name == null ? file.toString() : name.toString();
    }
}
