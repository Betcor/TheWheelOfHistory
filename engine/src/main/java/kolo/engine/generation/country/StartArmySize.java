package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ArmySizeId;
import kolo.engine.error.Checks;
import kolo.engine.wheel.RollRecord;

/**
 * Стартовий розмір армії (GD §4.1, колесо 10) — результат {@link ArmySizeWheel}. Чисельність армії — частка ×
 * населення; населення визначає колесо з картою.
 *
 * @param size рівень розміру армії з контенту
 * @param shareBp частка населення під зброєю, базисні пункти {@code 1..}{@value ArmySizeDef#MAX_SHARE_BP}
 * @param tags мітки рівня — вхід для наступних коліс генерації
 * @param quality якість рівня для стріків генерації (GD §4.10), {@code 0..100}
 * @param rolls обертання колеса розміру армії
 */
public record StartArmySize(ArmySizeId size, int shareBp, SortedSet<String> tags, int quality, List<RollRecord> rolls) {

    public StartArmySize {
        Objects.requireNonNull(size, "size");
        Checks.inRange("share_bp", shareBp, 1, ArmySizeDef.MAX_SHARE_BP);
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        Checks.inRange("quality", quality, 0, 100);
        rolls = List.copyOf(rolls);
    }
}
