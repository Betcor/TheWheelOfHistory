package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ рівня ВВП на душу в контенті ({@code snake_case}). */
public record GdpLevelId(String value) implements Comparable<GdpLevelId> {

    public GdpLevelId {
        Checks.snakeCase("gdp_level_id", value);
    }

    @Override
    public int compareTo(GdpLevelId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
