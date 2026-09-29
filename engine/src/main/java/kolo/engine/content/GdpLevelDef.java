package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.OutcomeTier;

/**
 * Рівень ВВП на душу (GD §4.1, колесо 8) і його сектор у колесі ВВП.
 *
 * <p>Рівні йдуть від найбіднішого до найбагатшого ({@link #checkFollows}): це порядок секторів, а рівень результату
 * визначає, в який бік їх зсуває перевага.
 *
 * @param name назва українською, напр. «Середній дохід»
 * @param description що означає рівень, для підказки гравцеві
 * @param perCapita ВВП на душу, умовні долари 1970 року на людину за рік, {@code 1..}{@value #MAX_PER_CAPITA};
 *     загальний ВВП — з населенням
 * @param tier як на рівень діє перевага: бідніші — провали, багатші — успіхи; крайні рівні зазвичай критичні, тоді
 *     вони не бувають рідше 1%
 * @param weight базова вага в колесі ВВП, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує ваги всіх
 *     рівнів, а перевага зсуває їх
 * @param quality наскільки рівень добрий для держави, {@code 0..100} (стріки генерації)
 * @param tags мітки, які рівень дає державі (напр. {@code poor}), — вхід для наступних коліс
 */
public record GdpLevelDef(
        GdpLevelId id,
        String name,
        String description,
        int perCapita,
        OutcomeTier tier,
        int weight,
        int quality,
        List<String> tags) {

    /** Найбільший ВВП на душу: із запасом, щоб множення на населення лишалося в {@code long}. */
    public static final int MAX_PER_CAPITA = 1_000_000;

    /** Найбільша відносна вага рівня в колесі ВВП. */
    public static final int MAX_WEIGHT = 10_000;

    public GdpLevelDef {
        Objects.requireNonNull(id, "gdp_level.id");
        Checks.notBlank("gdp_level." + id + ".name", name);
        Checks.notBlank("gdp_level." + id + ".description", description);
        Checks.inRange("gdp_level." + id + ".per_capita", perCapita, 1, MAX_PER_CAPITA);
        Objects.requireNonNull(tier, "gdp_level." + id + ".tier");
        Checks.inRange("gdp_level." + id + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange("gdp_level." + id + ".quality", quality, 0, 100);
        tags = Defs.tags("gdp_level." + id + ".tags", tags);
    }

    /**
     * {@code next} іде за {@code previous}: ВВП на душу строго більший, а рівень результату не нижчий. Інакше
     * додатна перевага могла б зсувати шанси до біднішого рівня.
     *
     * @throws ValidationException з {@link ErrorCode#OUT_OF_ORDER}
     */
    public static void checkFollows(GdpLevelDef previous, GdpLevelDef next) {
        if (next.perCapita <= previous.perCapita) {
            throw outOfOrder("gdp_level." + next.id + ".per_capita", next.perCapita);
        }
        if (next.tier.compareTo(previous.tier) < 0) {
            throw outOfOrder("gdp_level." + next.id + ".tier", next.tier.key());
        }
    }

    private static ValidationException outOfOrder(String field, Object value) {
        return new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", field, "value", value));
    }
}
