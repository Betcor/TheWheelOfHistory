package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.GdpLevelId;
import kolo.engine.error.Checks;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;

/**
 * Стартовий ВВП на душу (GD §4.1, колесо 8) — результат {@link GdpWheel}. Загальний ВВП держави — ВВП на душу ×
 * населення; населення визначає колесо з картою.
 *
 * @param level рівень ВВП з контенту
 * @param perCapita ВВП на душу рівня, умовні долари 1970 року на людину за рік
 * @param tier рівень результату рівня: наскільки ВВП бідніший чи багатший за звичайний
 * @param tags мітки рівня — вхід для наступних коліс генерації
 * @param quality якість рівня для стріків генерації (GD §4.10), {@code 0..100}
 * @param rolls обертання колеса ВВП
 */
public record StartGdp(
        GdpLevelId level,
        int perCapita,
        OutcomeTier tier,
        SortedSet<String> tags,
        int quality,
        List<RollRecord> rolls) {

    public StartGdp {
        Objects.requireNonNull(level, "level");
        Checks.inRange("per_capita", perCapita, 1, GdpLevelDef.MAX_PER_CAPITA);
        Objects.requireNonNull(tier, "tier");
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        Checks.inRange("quality", quality, 0, 100);
        rolls = List.copyOf(rolls);
    }

    /**
     * Внесок ВВП у перевагу наступного колеса генерації: крок рівня результату ({@link OutcomeTier#step()}) × {@code
     * perStep}. Рядок пояснення має id {@code gdp:<рівень>} і ключ {@code gdp.<рівень>}; на частковому рівні внеску
     * немає.
     */
    public Optional<AppliedModifier> advantage(int perStep) {
        int value = tier.step() * perStep;
        if (value == 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedModifier("gdp:" + level, "gdp." + level, value));
    }
}
