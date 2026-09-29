package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Stat;
import kolo.engine.wheel.OutcomeTier;

/**
 * Рівень індексу людського розвитку (GD §4.1, колесо 9) і його сектор у колесі ІЛР.
 *
 * <p>Рівні йдуть від найнижчого до найвищого ({@link #checkFollows}): це порядок секторів, а рівень результату
 * визначає, в який бік їх зсуває перевага.
 *
 * @param name назва українською, напр. «Середній розвиток»
 * @param description що означає рівень, для підказки гравцеві
 * @param hdi стартовий ІЛР держави на цьому рівні, в межах показника {@link Stat#HDI}
 * @param tier як на рівень діє перевага: нижчі — провали, вищі — успіхи; крайні рівні зазвичай критичні, тоді вони
 *     не бувають рідше 1%
 * @param weight базова вага в колесі ІЛР, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує ваги всіх
 *     рівнів, а перевага зсуває їх
 * @param quality наскільки рівень добрий для держави, {@code 0..100} (стріки генерації)
 * @param tags мітки, які рівень дає державі, — вхід для наступних коліс
 */
public record HdiLevelDef(
        HdiLevelId id,
        String name,
        String description,
        int hdi,
        OutcomeTier tier,
        int weight,
        int quality,
        List<String> tags) {

    /** Найбільша відносна вага рівня в колесі ІЛР. */
    public static final int MAX_WEIGHT = 10_000;

    public HdiLevelDef {
        Objects.requireNonNull(id, "hdi_level.id");
        Checks.notBlank("hdi_level." + id + ".name", name);
        Checks.notBlank("hdi_level." + id + ".description", description);
        Checks.inRange("hdi_level." + id + ".hdi", hdi, Stat.HDI.min(), Stat.HDI.max());
        Objects.requireNonNull(tier, "hdi_level." + id + ".tier");
        Checks.inRange("hdi_level." + id + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange("hdi_level." + id + ".quality", quality, 0, 100);
        tags = Defs.tags("hdi_level." + id + ".tags", tags);
    }

    /**
     * {@code next} іде за {@code previous}: ІЛР строго більший, а рівень результату не нижчий. Інакше додатна перевага
     * могла б зсувати шанси до нижчого розвитку.
     *
     * @throws ValidationException з {@link ErrorCode#OUT_OF_ORDER}
     */
    public static void checkFollows(HdiLevelDef previous, HdiLevelDef next) {
        if (next.hdi <= previous.hdi) {
            throw outOfOrder("hdi_level." + next.id + ".hdi", next.hdi);
        }
        if (next.tier.compareTo(previous.tier) < 0) {
            throw outOfOrder("hdi_level." + next.id + ".tier", next.tier.key());
        }
    }

    private static ValidationException outOfOrder(String field, Object value) {
        return new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", field, "value", value));
    }
}
