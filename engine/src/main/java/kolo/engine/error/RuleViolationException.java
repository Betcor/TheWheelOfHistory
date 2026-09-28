package kolo.engine.error;

import java.util.Map;

/** Дія суперечить правилам гри. */
public class RuleViolationException extends GameException {

    private static final long serialVersionUID = 1L;

    public RuleViolationException(ErrorCode code, Map<String, ?> details) {
        super(code, details);
    }

    public RuleViolationException(ErrorCode code, Map<String, ?> details, Throwable cause) {
        super(code, details, cause);
    }
}
