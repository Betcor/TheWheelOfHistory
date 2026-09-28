package kolo.engine.error;

import java.util.Map;

/** Помилка протоколу клієнт–сервер. */
public class ProtocolException extends GameException {

    private static final long serialVersionUID = 1L;

    public ProtocolException(ErrorCode code, Map<String, ?> details) {
        super(code, details);
    }

    public ProtocolException(ErrorCode code, Map<String, ?> details, Throwable cause) {
        super(code, details, cause);
    }
}
