package kolo.engine.wheel;

import java.util.List;

/**
 * Перевага колеса: сума внесків, обмежена {@code [−100, 100]}, разом із поясненнями.
 *
 * @param value підсумкова перевага, {@code −100..100}
 * @param modifiers внески, з яких її складено (у порядку додавання); сума може виходити за межі до обрізання
 */
public record Advantage(int value, List<AppliedModifier> modifiers) {

    public static final int MIN = -100;
    public static final int MAX = 100;

    /** Колесо без переваги. */
    public static final Advantage NONE = new Advantage(0, List.of());

    public Advantage {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("перевага поза −100..100: " + value);
        }
        modifiers = List.copyOf(modifiers);
    }

    /** Сума внесків, обрізана до {@code [−100, 100]}. */
    public static Advantage of(List<AppliedModifier> modifiers) {
        long sum = 0;
        for (AppliedModifier modifier : modifiers) {
            sum += modifier.value();
        }
        return new Advantage(Math.clamp(sum, MIN, MAX), modifiers);
    }
}
