package kolo.engine.error;

import java.util.Map;

/** Не вистачає грошей у скарбниці. */
public class InsufficientFundsException extends RuleViolationException {

    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(Map<String, ?> details) {
        super(ErrorCode.INSUFFICIENT_FUNDS, details);
    }
}
