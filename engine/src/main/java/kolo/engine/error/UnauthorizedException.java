package kolo.engine.error;

import java.util.Map;

/** Невалідний токен гравця. */
public class UnauthorizedException extends AuthException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedException(Map<String, ?> details) {
        super(ErrorCode.UNAUTHORIZED, details);
    }
}
