package kolo.engine.error;

import java.util.Map;

/** Контент невалідний; фатально при старті гри чи сервера. */
public class ContentException extends GameException {

    private static final long serialVersionUID = 1L;

    public ContentException(ErrorCode code, Map<String, ?> details) {
        super(code, details);
    }

    public ContentException(ErrorCode code, Map<String, ?> details, Throwable cause) {
        super(code, details, cause);
    }
}
