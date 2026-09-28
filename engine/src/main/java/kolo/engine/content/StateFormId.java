package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ форми державності в контенті ({@code snake_case}). */
public record StateFormId(String value) implements Comparable<StateFormId> {

    public StateFormId {
        Checks.snakeCase("state_form_id", value);
    }

    @Override
    public int compareTo(StateFormId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
