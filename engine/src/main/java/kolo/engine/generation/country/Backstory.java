package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.SourceKind;
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

    /**
     * Якість передісторії для стріків генерації (GD §4.10): одна на всю передісторію — середня якість фрагментів,
     * округлена вниз. Пов'язані фрагменти («програна війна» → «контрибуції») інакше самі собою складали б стрік.
     *
     * @return порожньо, якщо фрагментів немає: тоді передісторія не рахується в стріки
     */
    public OptionalInt quality() {
        if (entries.isEmpty()) {
            return OptionalInt.empty();
        }
        int sum = 0;
        for (BackstoryEntry entry : entries) {
            sum += entry.fragment().quality();
        }
        return OptionalInt.of(Math.floorDiv(sum, entries.size()));
    }

    /**
     * Модифікатори фрагментів у хронологічному порядку, кожного — в порядку контенту. Діють {@code duration} років від
     * 01.01.1970: останній хід дії — {@code duration − 1}; {@code 0} — постійно.
     *
     * <p>Id — {@code backstory:<фрагмент>:<номер>}; ключ пояснення — {@code backstory.<фрагмент>}: клієнт показує в
     * ньому текст фрагмента.
     */
    public List<Modifier> modifiers() {
        List<Modifier> result = new ArrayList<>();
        for (BackstoryEntry entry : entries) {
            BackstoryFragmentDef fragment = entry.fragment();
            String refId = fragment.id().value();
            ModifierSource source = new ModifierSource(SourceKind.BACKSTORY, refId);
            Integer expiresAtTurn = fragment.durationYears() == 0 ? null : fragment.durationYears() - 1;
            for (int i = 0; i < fragment.modifiers().size(); i++) {
                result.add(fragment.modifiers()
                        .get(i)
                        .toModifier("backstory:" + refId + ":" + i, source, expiresAtTurn, "backstory." + refId));
            }
        }
        return List.copyOf(result);
    }
}
