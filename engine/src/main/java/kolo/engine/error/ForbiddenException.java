package kolo.engine.error;

import java.util.Map;

/** Немає прав на дію (напр. гравець не хост). */
public class ForbiddenException extends AuthException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(Map<String, ?> details) {
        super(ErrorCode.FORBIDDEN, details);
    }
}
