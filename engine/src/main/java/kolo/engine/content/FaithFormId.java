package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ форми назви віри в контенті ({@code snake_case}). */
public record FaithFormId(String value) implements Comparable<FaithFormId> {

    public FaithFormId {
        Checks.snakeCase("faith_form_id", value);
    }

    @Override
    public int compareTo(FaithFormId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
