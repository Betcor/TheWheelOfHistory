package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ шаблону карти в контенті ({@code snake_case}), напр. {@code archipelago}. */
public record MapTemplateId(String value) implements Comparable<MapTemplateId> {

    public MapTemplateId {
        Checks.snakeCase("map_template_id", value);
    }

    @Override
    public int compareTo(MapTemplateId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
