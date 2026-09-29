package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ аспекту божества в контенті ({@code snake_case}). */
public record AspectId(String value) implements Comparable<AspectId> {

    public AspectId {
        Checks.snakeCase("aspect_id", value);
    }

    @Override
    public int compareTo(AspectId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
