package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.state.NuclearStatus;
import kolo.engine.wheel.RollRecord;

/**
 * Стартовий ядерний статус держави (GD §4.6) — результат {@link NuclearWheel}.
 *
 * @param warheads кількість боєголовок: більша за нуль для арсеналу й нуль для решти статусів
 * @param tags мітки статусу — вхід для наступних коліс генерації (передісторії)
 * @param quality якість статусу для стріків генерації (GD §4.10), {@code 0..100}
 * @param rolls обертання в порядку кидків: статус, потім (для арсеналу) кількість боєголовок
 */
public record StartNuclear(
        NuclearStatus status, int warheads, SortedSet<String> tags, int quality, List<RollRecord> rolls) {

    public StartNuclear {
        Objects.requireNonNull(status, "status");
        if (status == NuclearStatus.ARSENAL) {
            Checks.inRange("warheads", warheads, 1, Integer.MAX_VALUE);
        } else {
            Checks.inRange("warheads", warheads, 0, 0);
        }
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        Checks.inRange("quality", quality, 0, 100);
        rolls = List.copyOf(rolls);
    }
}
