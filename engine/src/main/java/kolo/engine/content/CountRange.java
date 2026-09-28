package kolo.engine.content;

import kolo.engine.error.Checks;

/**
 * Скільки разів щось відбувається: від {@code min} до {@code max} включно.
 *
 * @param min {@code ≥ 0}
 * @param max {@code ≥ min}
 */
public record CountRange(int min, int max) {

    public CountRange {
        Checks.inRange("min", min, 0, Integer.MAX_VALUE);
        Checks.inRange("max", max, min, Integer.MAX_VALUE);
    }

    public boolean contains(int count) {
        return count >= min && count <= max;
    }
}
