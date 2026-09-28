package kolo.engine.error;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/** Створює виняток за кодом через його клас — щоб тести проходили всі коди без ручного переліку. */
final class Errors {

    private Errors() {}

    static GameException create(ErrorCode code, Map<String, ?> details) {
        Class<? extends GameException> type = code.exceptionType();
        try {
            try {
                return type.getConstructor(Map.class).newInstance(details);
            } catch (NoSuchMethodException fixedCodeMissing) {
                return type.getConstructor(ErrorCode.class, Map.class).newInstance(code, details);
            }
        } catch (InvocationTargetException e) {
            throw (RuntimeException) e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("немає публічного конструктора в " + type.getSimpleName(), e);
        }
    }
}
