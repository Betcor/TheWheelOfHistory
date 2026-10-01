package kolo.protocol.message;

import java.util.Locale;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Нікнейм гравця: те, що бачать інші в лобі й у списку гравців. Правило спільне для сервера (перевіряє повідомлення) і
 * клієнта (перевіряє поле введення до надсилання).
 */
public final class Nicknames {

    /** Найдовший нікнейм у символах Unicode. */
    public static final int MAX_LENGTH = 24;

    private Nicknames() {}

    /**
     * @return той самий нікнейм
     * @throws ValidationException якщо нікнейм порожній ({@code BLANK_VALUE}), задовгий ({@code VALUE_OUT_OF_RANGE})
     *     або має пробіли по краях чи керівні символи ({@code INVALID_NAME_FORMAT})
     */
    public static String check(String field, String nickname) {
        Checks.notBlank(field, nickname);
        Checks.inRange(field, nickname.codePointCount(0, nickname.length()), 1, MAX_LENGTH);
        boolean control = nickname.codePoints().anyMatch(Character::isISOControl);
        if (control || !nickname.strip().equals(nickname)) {
            throw new ValidationException(
                    ErrorCode.INVALID_NAME_FORMAT, ErrorDetails.of("field", field, "value", nickname));
        }
        return nickname;
    }

    /** Ключ порівняння: нікнейми, що різняться лише регістром, — однакові. */
    public static String key(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }
}
