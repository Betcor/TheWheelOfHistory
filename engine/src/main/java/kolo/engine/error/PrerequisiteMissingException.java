package kolo.engine.error;

import java.util.Map;

/** Немає потрібної технології, ресурсу чи будівлі. */
public class PrerequisiteMissingException extends RuleViolationException {

    private static final long serialVersionUID = 1L;

    public PrerequisiteMissingException(Map<String, ?> details) {
        super(ErrorCode.PREREQUISITE_MISSING, details);
    }
}
