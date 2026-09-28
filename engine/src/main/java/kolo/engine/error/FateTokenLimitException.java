package kolo.engine.error;

import java.util.Map;

/** Спроба перевищити ліміт жетонів долі. */
public class FateTokenLimitException extends RuleViolationException {

    private static final long serialVersionUID = 1L;

    public FateTokenLimitException(Map<String, ?> details) {
        super(ErrorCode.FATE_TOKEN_LIMIT, details);
    }
}
