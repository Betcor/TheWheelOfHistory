package kolo.engine.error;

import java.util.Map;

/** Різні версії протоколу або хеш контенту клієнта й сервера. */
public class VersionMismatchException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public VersionMismatchException(Map<String, ?> details) {
        super(ErrorCode.VERSION_MISMATCH, details);
    }
}
