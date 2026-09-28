package kolo.engine.error;

import java.util.Map;

/** Файл світу створено новішою версією гри. */
public class SaveVersionException extends SaveFileException {

    private static final long serialVersionUID = 1L;

    public SaveVersionException(Map<String, ?> details) {
        super(ErrorCode.SAVE_VERSION_TOO_NEW, details);
    }
}
