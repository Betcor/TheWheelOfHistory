package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.PopulationLevelDef;
import kolo.engine.content.PopulationLevelId;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;

/**
 * Стартове населення держави (GD §4.1, колесо 4) — результат {@link PopulationWheel}.
 *
 * @param level рівень населення з контенту
 * @param populationK населення держави в тисячах
 * @param tier рівень результату рівня: наскільки населення менше чи більше за звичайне
 * @param tags мітки рівня — вхід для наступних коліс генерації
 * @param quality якість рівня для стріків генерації (GD §4.10), {@code 0..100}
 * @param provinces провінція → населення в тисячах (майбутнє {@code Province.populationK}); разом — {@code
 *     populationK}; мала держава з великою кількістю провінцій може мати провінції з нулем
 * @param rolls обертання колеса населення
 */
public record StartPopulation(
        PopulationLevelId level,
        int populationK,
        OutcomeTier tier,
        SortedSet<String> tags,
        int quality,
        SortedMap<Integer, Integer> provinces,
        List<RollRecord> rolls) {

    public StartPopulation {
        Objects.requireNonNull(level, "level");
        Checks.inRange("population_k", populationK, 1, PopulationLevelDef.MAX_POPULATION_K);
        Objects.requireNonNull(tier, "tier");
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        Checks.inRange("quality", quality, 0, 100);
        provinces = Collections.unmodifiableSortedMap(new TreeMap<>(provinces));
        Checks.inRange("provinces", provinces.size(), 1, Integer.MAX_VALUE);
        long total = 0;
        for (Map.Entry<Integer, Integer> entry : provinces.entrySet()) {
            Checks.inRange("province", entry.getKey(), 0, Integer.MAX_VALUE);
            Checks.inRange("province_population_k", entry.getValue(), 0, populationK);
            total += entry.getValue();
        }
        if (total != populationK) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "provinces", "value", total));
        }
        rolls = List.copyOf(rolls);
    }

    /**
     * Внесок населення в перевагу наступного колеса генерації: крок рівня результату ({@link OutcomeTier#step()}) ×
     * {@code perStep}. Рядок пояснення має id {@code population:<рівень>} і ключ {@code population.<рівень>}; на
     * частковому рівні внеску немає.
     */
    public Optional<AppliedModifier> advantage(int perStep) {
        int value = tier.step() * perStep;
        if (value == 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedModifier("population:" + level, "population." + level, value));
    }
}
