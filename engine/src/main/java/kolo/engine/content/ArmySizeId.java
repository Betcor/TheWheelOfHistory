package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ рівня розміру армії в контенті ({@code snake_case}). */
public record ArmySizeId(String value) implements Comparable<ArmySizeId> {

    public ArmySizeId {
        Checks.snakeCase("army_size_id", value);
    }

    @Override
    public int compareTo(ArmySizeId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
