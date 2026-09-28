package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ доктрини в контенті ({@code snake_case}). */
public record DoctrineId(String value) implements Comparable<DoctrineId> {

    public DoctrineId {
        Checks.snakeCase("doctrine_id", value);
    }

    @Override
    public int compareTo(DoctrineId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
