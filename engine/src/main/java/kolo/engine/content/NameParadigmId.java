package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ парадигми відмінювання назви в контенті ({@code snake_case}). */
public record NameParadigmId(String value) implements Comparable<NameParadigmId> {

    public NameParadigmId {
        Checks.snakeCase("name_paradigm_id", value);
    }

    @Override
    public int compareTo(NameParadigmId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
