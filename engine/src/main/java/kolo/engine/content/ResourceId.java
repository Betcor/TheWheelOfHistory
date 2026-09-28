package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ ресурсу в контенті ({@code snake_case}). */
public record ResourceId(String value) implements Comparable<ResourceId> {

    public ResourceId {
        Checks.snakeCase("resource_id", value);
    }

    @Override
    public int compareTo(ResourceId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
