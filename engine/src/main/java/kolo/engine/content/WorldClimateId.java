package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ клімату світу в контенті ({@code snake_case}), напр. {@code warm}. */
public record WorldClimateId(String value) implements Comparable<WorldClimateId> {

    public WorldClimateId {
        Checks.snakeCase("world_climate_id", value);
    }

    @Override
    public int compareTo(WorldClimateId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
