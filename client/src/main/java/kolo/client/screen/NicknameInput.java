package kolo.client.screen;

import java.util.Optional;
import kolo.engine.error.ValidationException;
import kolo.protocol.message.Nicknames;

/** Нікнейм з поля введення: пробіли по краях прибираються, далі — правило протоколу ({@link Nicknames}). */
public final class NicknameInput {

    private NicknameInput() {}

    /** @return нікнейм або порожньо, якщо він не відповідає правилу */
    public static Optional<String> parse(String text) {
        String trimmed = text == null ? "" : text.strip();
        try {
            return Optional.of(Nicknames.check("nickname", trimmed));
        } catch (ValidationException e) {
            return Optional.empty();
        }
    }
}
