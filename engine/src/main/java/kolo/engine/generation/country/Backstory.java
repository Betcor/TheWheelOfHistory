package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.state.CountryId;
import kolo.engine.wheel.RollRecord;

/**
 * Передісторія держави (GD §4.7) — результат {@link BackstoryWheel}.
 *
 * @param entries фрагменти в хронологічному порядку: рік кожного не раніший за рік попереднього
 * @param neighbor сусід, з яким пов'язують фрагменти з {@code neighbor}; порожньо, якщо таких фрагментів немає
 * @param tags мітки держави після передісторії: початкові разом із мітками фрагментів
 * @param rolls обертання в порядку кидків: кількість фрагментів, далі по одному на фрагмент
 */
public record Backstory(
        List<BackstoryEntry> entries, Optional<CountryId> neighbor, SortedSet<String> tags, List<RollRecord> rolls) {

    public Backstory {
        entries = List.copyOf(entries);
        Objects.requireNonNull(neighbor, "neighbor");
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        rolls = List.copyOf(rolls);
    }
}
