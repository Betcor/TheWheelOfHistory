package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.error.Checks;
import kolo.engine.state.Training;
import kolo.engine.wheel.RollRecord;

/**
 * Стартовий вишкіл армії (GD §4.1, колесо 12) — результат {@link ArmyTrainingWheel}. Прогрес усередині рівня
 * з'явиться разом з арміями.
 *
 * @param level рівень вишколу, {@link Training#MIN}..{@link Training#MAX}
 * @param combatModifier модифікатор вишколу в боях з контенту, {@code
 *     ±}{@value TrainingLevelDef#MAX_COMBAT_MODIFIER}
 * @param tags мітки рівня — вхід для наступних коліс генерації
 * @param quality якість рівня для стріків генерації (GD §4.10), {@code 0..100}
 * @param rolls обертання колеса вишколу
 */
public record StartArmyTraining(
        int level, int combatModifier, SortedSet<String> tags, int quality, List<RollRecord> rolls) {

    public StartArmyTraining {
        Training.check("level", level);
        Checks.inRange(
                "combat_modifier",
                combatModifier,
                -TrainingLevelDef.MAX_COMBAT_MODIFIER,
                TrainingLevelDef.MAX_COMBAT_MODIFIER);
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        Checks.inRange("quality", quality, 0, 100);
        rolls = List.copyOf(rolls);
    }
}
