package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ устрою релігії в контенті ({@code snake_case}). */
public record ReligionPolityId(String value) implements Comparable<ReligionPolityId> {

    public ReligionPolityId {
        Checks.snakeCase("religion_polity_id", value);
    }

    @Override
    public int compareTo(ReligionPolityId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
