package kolo.engine.error;

import java.util.Map;

/** Баг рушія: стан неконсистентний. Єдиний виняток, допустимий під час розрахунку ходу. */
public class InvariantViolationException extends GameException {

    private static final long serialVersionUID = 1L;

    public InvariantViolationException(Map<String, ?> details) {
        super(ErrorCode.INVARIANT_VIOLATION, details);
    }
}
