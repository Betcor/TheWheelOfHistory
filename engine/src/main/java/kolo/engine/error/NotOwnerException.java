package kolo.engine.error;

import java.util.Map;

/** Об'єкт не належить державі гравця. */
public class NotOwnerException extends RuleViolationException {

    private static final long serialVersionUID = 1L;

    public NotOwnerException(Map<String, ?> details) {
        super(ErrorCode.NOT_OWNER, details);
    }
}
