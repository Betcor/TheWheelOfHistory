package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.modifier.Modifier;
import kolo.engine.wheel.RollRecord;

/**
 * Релігія держави на старті (GD §4.1, 6а; §25.2) — результат {@link StateReligionWheel}: державна релігія або
 * світська держава.
 *
 * @param religion номер державної релігії серед релігій світу ({@code StartReligions.religions()}); порожньо —
 *     світська держава. Id релігій з'являться разом зі світом.
 * @param tags мітки релігії або світської держави — вхід для наступних коліс генерації
 * @param modifiers постійні модифікатори догматів і устрою державної релігії; у світської держави — порожньо
 * @param rolls обертання колеса релігії
 */
public record StartStateReligion(
        OptionalInt religion, SortedSet<String> tags, List<Modifier> modifiers, List<RollRecord> rolls) {

    public StartStateReligion {
        Objects.requireNonNull(religion, "religion");
        religion.ifPresent(index -> Checks.inRange("state_religion.religion", index, 0, Integer.MAX_VALUE));
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        modifiers = List.copyOf(modifiers);
        rolls = List.copyOf(rolls);
    }

    /** Чи держава світська — без державної релігії. */
    public boolean secular() {
        return religion.isEmpty();
    }
}
