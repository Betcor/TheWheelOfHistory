package kolo.engine.error;

import java.util.Map;

/** Наказ некоректний структурно. */
public class InvalidOrderException extends ValidationException {

    private static final long serialVersionUID = 1L;

    public InvalidOrderException(Map<String, ?> details) {
        super(ErrorCode.INVALID_ORDER, details);
    }
}
