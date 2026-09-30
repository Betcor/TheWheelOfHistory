package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ рівня виходу до моря в контенті ({@code snake_case}). */
public record CoastLevelId(String value) implements Comparable<CoastLevelId> {

    public CoastLevelId {
        Checks.snakeCase("coast_level_id", value);
    }

    @Override
    public int compareTo(CoastLevelId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
