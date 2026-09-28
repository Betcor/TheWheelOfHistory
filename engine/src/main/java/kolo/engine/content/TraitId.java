package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ риси відомої постаті в контенті ({@code snake_case}). */
public record TraitId(String value) implements Comparable<TraitId> {

    public TraitId {
        Checks.snakeCase("trait_id", value);
    }

    @Override
    public int compareTo(TraitId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
