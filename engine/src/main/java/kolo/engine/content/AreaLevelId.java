package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ рівня площі держави в контенті ({@code snake_case}). */
public record AreaLevelId(String value) implements Comparable<AreaLevelId> {

    public AreaLevelId {
        Checks.snakeCase("area_level_id", value);
    }

    @Override
    public int compareTo(AreaLevelId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
