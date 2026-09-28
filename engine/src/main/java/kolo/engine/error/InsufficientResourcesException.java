package kolo.engine.error;

import java.util.Map;

/** Не вистачає ресурсів. */
public class InsufficientResourcesException extends RuleViolationException {

    private static final long serialVersionUID = 1L;

    public InsufficientResourcesException(Map<String, ?> details) {
        super(ErrorCode.INSUFFICIENT_RESOURCES, details);
    }
}
