package kolo.engine.error;

import java.util.Map;

/** Помилка автентифікації чи прав доступу. */
public class AuthException extends GameException {

    private static final long serialVersionUID = 1L;

    public AuthException(ErrorCode code, Map<String, ?> details) {
        super(code, details);
    }

    public AuthException(ErrorCode code, Map<String, ?> details, Throwable cause) {
        super(code, details, cause);
    }
}
