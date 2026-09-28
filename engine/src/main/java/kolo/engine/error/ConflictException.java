package kolo.engine.error;

import java.util.Map;

/** Застарілий {@code seq} або конкурентна зміна. */
public class ConflictException extends GameException {

    private static final long serialVersionUID = 1L;

    public ConflictException(ErrorCode code, Map<String, ?> details) {
        super(code, details);
    }

    public ConflictException(ErrorCode code, Map<String, ?> details, Throwable cause) {
        super(code, details, cause);
    }
}
