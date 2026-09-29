package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Training;
import kolo.engine.wheel.OutcomeTier;

/**
 * Рівень вишколу армії (GD §4.1, колесо 12; GD §4.4) і його сектор у колесі вишколу.
 *
 * <p>Рівні йдуть від ополчення до еліти ({@link #checkFollows}): це порядок секторів, а рівень результату визначає, в
 * який бік їх зсуває перевага.
 *
 * @param level {@link Training#MIN}..{@link Training#MAX}
 * @param name назва українською, напр. «Регулярна армія»
 * @param description що означає рівень, для підказки гравцеві
 * @param combatModifier модифікатор вишколу в боях (GD §4.4), {@code −}{@value #MAX_COMBAT_MODIFIER}{@code
 *     ..}{@value #MAX_COMBAT_MODIFIER}; застосує система війни
 * @param tier як на рівень діє перевага: гірший вишкіл — провали, кращий — успіхи; крайні рівні зазвичай критичні,
 *     тоді вони не бувають рідше 1%
 * @param weight базова вага в колесі вишколу, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує ваги
 *     всіх рівнів, а перевага зсуває їх
 * @param quality наскільки рівень добрий для держави, {@code 0..100} (стріки генерації)
 * @param tags мітки, які рівень дає державі, — вхід для наступних коліс
 */
public record TrainingLevelDef(
        int level,
        String name,
        String description,
        int combatModifier,
        OutcomeTier tier,
        int weight,
        int quality,
        List<String> tags) {

    /** Модифікатор вишколу — одна зі складових переваги в бою, тож не більший за саму перевагу. */
    public static final int MAX_COMBAT_MODIFIER = 100;

    /** Найбільша відносна вага рівня в колесі вишколу. */
    public static final int MAX_WEIGHT = 10_000;

    public TrainingLevelDef {
        Training.check("training_level.level", level);
        Checks.notBlank("training_level." + level + ".name", name);
        Checks.notBlank("training_level." + level + ".description", description);
        Checks.inRange(
                "training_level." + level + ".combat_modifier",
                combatModifier,
                -MAX_COMBAT_MODIFIER,
                MAX_COMBAT_MODIFIER);
        Objects.requireNonNull(tier, "training_level." + level + ".tier");
        Checks.inRange("training_level." + level + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange("training_level." + level + ".quality", quality, 0, 100);
        tags = Defs.tags("training_level." + level + ".tags", tags);
    }

    /**
     * {@code next} іде за {@code previous}: рівень і модифікатор строго більші, а рівень результату не нижчий. Інакше
     * додатна перевага могла б зсувати шанси до гіршого вишколу.
     *
     * @throws ValidationException з {@link ErrorCode#OUT_OF_ORDER}
     */
    public static void checkFollows(TrainingLevelDef previous, TrainingLevelDef next) {
        if (next.level <= previous.level) {
            throw outOfOrder("training_level." + next.level + ".level", next.level);
        }
        if (next.combatModifier <= previous.combatModifier) {
            throw outOfOrder("training_level." + next.level + ".combat_modifier", next.combatModifier);
        }
        if (next.tier.compareTo(previous.tier) < 0) {
            throw outOfOrder("training_level." + next.level + ".tier", next.tier.key());
        }
    }

    private static ValidationException outOfOrder(String field, Object value) {
        return new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", field, "value", value));
    }
}
