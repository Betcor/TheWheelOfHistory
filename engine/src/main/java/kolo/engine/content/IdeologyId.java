package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ ідеології в контенті ({@code snake_case}). */
public record IdeologyId(String value) implements Comparable<IdeologyId> {

    public IdeologyId {
        Checks.snakeCase("ideology_id", value);
    }

    @Override
    public int compareTo(IdeologyId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
