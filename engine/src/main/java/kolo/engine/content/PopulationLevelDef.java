package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.OutcomeTier;

/**
 * Рівень населення держави (GD §4.1, колесо 4) і його сектор у колесі населення.
 *
 * <p>Населення — загальне число людей держави, а не щільність (рішення автора): площа й родючість лише зсувають
 * шанси перевагою. Рівні йдуть від найменшого до найбільшого ({@link #checkFollows}): це порядок секторів, а рівень
 * результату визначає, в який бік їх зсуває перевага.
 *
 * @param name назва українською, напр. «Багатолюдна держава»
 * @param description що означає рівень, для підказки гравцеві
 * @param populationK населення в тисячах, {@code 1..}{@value #MAX_POPULATION_K}
 * @param tier як на рівень діє перевага: менші — провали, більші — успіхи
 * @param weight базова вага в колесі населення, {@code 1..}{@value #MAX_WEIGHT}; відносна
 * @param quality наскільки рівень добрий для держави, {@code 0..100} (стріки генерації)
 * @param tags мітки, які рівень дає державі (напр. {@code large_population}), — вхід для наступних коліс
 */
public record PopulationLevelDef(
        PopulationLevelId id,
        String name,
        String description,
        int populationK,
        OutcomeTier tier,
        int weight,
        int quality,
        List<String> tags) {

    /** Десять мільярдів — більше за населення Землі; із запасом, щоб множення на ВВП на душу лишалося в {@code long}. */
    public static final int MAX_POPULATION_K = 10_000_000;

    /** Найбільша відносна вага рівня в колесі населення. */
    public static final int MAX_WEIGHT = 10_000;

    public PopulationLevelDef {
        Objects.requireNonNull(id, "population_level.id");
        Checks.notBlank("population_level." + id + ".name", name);
        Checks.notBlank("population_level." + id + ".description", description);
        Checks.inRange("population_level." + id + ".population_k", populationK, 1, MAX_POPULATION_K);
        Objects.requireNonNull(tier, "population_level." + id + ".tier");
        Checks.inRange("population_level." + id + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange("population_level." + id + ".quality", quality, 0, 100);
        tags = Defs.tags("population_level." + id + ".tags", tags);
    }

    /**
     * {@code next} іде за {@code previous}: населення строго більше, а рівень результату не нижчий. Інакше додатна
     * перевага могла б зсувати шанси до меншого населення.
     *
     * @throws ValidationException з {@link ErrorCode#OUT_OF_ORDER}
     */
    public static void checkFollows(PopulationLevelDef previous, PopulationLevelDef next) {
        if (next.populationK <= previous.populationK) {
            throw outOfOrder("population_level." + next.id + ".population_k", next.populationK);
        }
        if (next.tier.compareTo(previous.tier) < 0) {
            throw outOfOrder("population_level." + next.id + ".tier", next.tier.key());
        }
    }

    private static ValidationException outOfOrder(String field, Object value) {
        return new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", field, "value", value));
    }
}
