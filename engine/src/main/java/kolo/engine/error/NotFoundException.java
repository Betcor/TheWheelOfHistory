package kolo.engine.error;

import java.util.Map;

/** Світ, держава, провінція чи інший об'єкт не існує. */
public class NotFoundException extends GameException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(ErrorCode code, Map<String, ?> details) {
        super(code, details);
    }

    public NotFoundException(ErrorCode code, Map<String, ?> details, Throwable cause) {
        super(code, details, cause);
    }
}
