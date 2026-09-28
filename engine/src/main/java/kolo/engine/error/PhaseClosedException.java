package kolo.engine.error;

import java.util.Map;

/** Фаза наказів закрита. */
public class PhaseClosedException extends RuleViolationException {

    private static final long serialVersionUID = 1L;

    public PhaseClosedException(Map<String, ?> details) {
        super(ErrorCode.PHASE_CLOSED, details);
    }
}
