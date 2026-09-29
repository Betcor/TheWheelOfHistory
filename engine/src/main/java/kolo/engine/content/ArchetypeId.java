package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ архетипу релігії в контенті ({@code snake_case}). */
public record ArchetypeId(String value) implements Comparable<ArchetypeId> {

    public ArchetypeId {
        Checks.snakeCase("archetype_id", value);
    }

    @Override
    public int compareTo(ArchetypeId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
