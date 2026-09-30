package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ рівня населення держави в контенті ({@code snake_case}). */
public record PopulationLevelId(String value) implements Comparable<PopulationLevelId> {

    public PopulationLevelId {
        Checks.snakeCase("population_level_id", value);
    }

    @Override
    public int compareTo(PopulationLevelId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
