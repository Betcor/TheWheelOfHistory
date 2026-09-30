package kolo.client.screen;

import java.util.OptionalLong;

/** Seed світу з поля введення. */
public final class SeedInput {

    private SeedInput() {}

    /**
     * Ціле число зі знаком — цей seed; порожнє поле — порожньо: seed обере клієнт випадково.
     *
     * @throws NumberFormatException якщо введено не ціле число в межах {@code long}
     */
    public static OptionalLong parse(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(trimmed));
    }
}
