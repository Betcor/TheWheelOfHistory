package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ догмату релігії в контенті ({@code snake_case}). */
public record DogmaId(String value) implements Comparable<DogmaId> {

    public DogmaId {
        Checks.snakeCase("dogma_id", value);
    }

    @Override
    public int compareTo(DogmaId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
