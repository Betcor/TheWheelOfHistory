package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ мовного стилю назв в контенті ({@code snake_case}). */
public record NameStyleId(String value) implements Comparable<NameStyleId> {

    public NameStyleId {
        Checks.snakeCase("name_style_id", value);
    }

    @Override
    public int compareTo(NameStyleId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
