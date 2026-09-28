package kolo.engine.error;

import java.util.Map;

/** Вхід не відповідає формату: значення поза межами, порожнє поле, невалідний ключ. */
public class ValidationException extends GameException {

    private static final long serialVersionUID = 1L;

    public ValidationException(ErrorCode code, Map<String, ?> details) {
        super(code, details);
    }

    public ValidationException(ErrorCode code, Map<String, ?> details, Throwable cause) {
        super(code, details, cause);
    }
}
