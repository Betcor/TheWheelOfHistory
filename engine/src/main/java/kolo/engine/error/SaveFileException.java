package kolo.engine.error;

import java.util.Map;

/** Помилка файлу світу чи SQLite. */
public class SaveFileException extends GameException {

    private static final long serialVersionUID = 1L;

    public SaveFileException(ErrorCode code, Map<String, ?> details) {
        super(code, details);
    }

    public SaveFileException(ErrorCode code, Map<String, ?> details, Throwable cause) {
        super(code, details, cause);
    }
}
