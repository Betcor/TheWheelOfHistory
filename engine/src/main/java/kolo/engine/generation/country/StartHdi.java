package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.HdiLevelId;
import kolo.engine.error.Checks;
import kolo.engine.state.Stat;
import kolo.engine.wheel.RollRecord;

/**
 * Стартовий індекс людського розвитку (GD §4.1, колесо 9) — результат {@link HdiWheel}.
 *
 * @param level рівень ІЛР з контенту
 * @param hdi базовий ІЛР держави — значення рівня, в межах показника {@link Stat#HDI}; модифікатори ладу й
 *     передісторії діють поверх нього
 * @param tags мітки рівня — вхід для наступних коліс генерації
 * @param quality якість рівня для стріків генерації (GD §4.10), {@code 0..100}
 * @param rolls обертання колеса ІЛР
 */
public record StartHdi(HdiLevelId level, int hdi, SortedSet<String> tags, int quality, List<RollRecord> rolls) {

    public StartHdi {
        Objects.requireNonNull(level, "level");
        Checks.inRange("hdi", hdi, Stat.HDI.min(), Stat.HDI.max());
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        Checks.inRange("quality", quality, 0, 100);
        rolls = List.copyOf(rolls);
    }
}
