package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ фрагмента передісторії в контенті ({@code snake_case}). */
public record BackstoryFragmentId(String value) implements Comparable<BackstoryFragmentId> {

    public BackstoryFragmentId {
        Checks.snakeCase("backstory_fragment_id", value);
    }

    @Override
    public int compareTo(BackstoryFragmentId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
