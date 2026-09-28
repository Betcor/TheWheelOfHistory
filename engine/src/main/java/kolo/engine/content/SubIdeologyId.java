package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ підкласифікації в контенті ({@code snake_case}). */
public record SubIdeologyId(String value) implements Comparable<SubIdeologyId> {

    public SubIdeologyId {
        Checks.snakeCase("sub_ideology_id", value);
    }

    @Override
    public int compareTo(SubIdeologyId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
