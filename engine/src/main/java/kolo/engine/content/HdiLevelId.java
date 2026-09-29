package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ рівня індексу людського розвитку в контенті ({@code snake_case}). */
public record HdiLevelId(String value) implements Comparable<HdiLevelId> {

    public HdiLevelId {
        Checks.snakeCase("hdi_level_id", value);
    }

    @Override
    public int compareTo(HdiLevelId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
