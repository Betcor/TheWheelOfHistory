package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Рівень площі держави (GD §4.1, колесо 2) і його сектор у колесі площі.
 *
 * <p>Площа — частка середньої: середня держава займає {@code суходіл × (1 − нічийні) / держав} провінцій. Після
 * коліс усіх держав площі масштабуються так, щоб разом зайняти рівно стільки суходолу (ADR 0033), тож рівень задає
 * співвідношення розмірів, а не точну кількість провінцій. Рівні йдуть від найменшого до найбільшого
 * ({@link #checkFollows}) — це порядок секторів.
 *
 * @param name назва українською, напр. «Велика держава»
 * @param description що означає рівень, для підказки гравцеві
 * @param sharePct площа у відсотках середньої, {@code 1..}{@value #MAX_SHARE_PCT}
 * @param weight вага в колесі площі, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує ваги всіх рівнів
 * @param quality наскільки рівень добрий для держави, {@code 0..100} (стріки генерації)
 * @param tags мітки, які рівень дає державі (напр. {@code small_country}), — вхід для наступних коліс
 */
public record AreaLevelDef(
        AreaLevelId id, String name, String description, int sharePct, int weight, int quality, List<String> tags) {

    /** Держава в десять разів більша за середню — уже половина світу навіть серед 20 держав. */
    public static final int MAX_SHARE_PCT = 1_000;

    /** Найбільша відносна вага рівня в колесі площі. */
    public static final int MAX_WEIGHT = 10_000;

    public AreaLevelDef {
        Objects.requireNonNull(id, "area_level.id");
        Checks.notBlank("area_level." + id + ".name", name);
        Checks.notBlank("area_level." + id + ".description", description);
        Checks.inRange("area_level." + id + ".share_pct", sharePct, 1, MAX_SHARE_PCT);
        Checks.inRange("area_level." + id + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange("area_level." + id + ".quality", quality, 0, 100);
        tags = Defs.tags("area_level." + id + ".tags", tags);
    }

    /**
     * {@code next} іде за {@code previous}: частка строго більша.
     *
     * @throws ValidationException з {@link ErrorCode#OUT_OF_ORDER}
     */
    public static void checkFollows(AreaLevelDef previous, AreaLevelDef next) {
        if (next.sharePct <= previous.sharePct) {
            throw new ValidationException(
                    ErrorCode.OUT_OF_ORDER,
                    ErrorDetails.of("field", "area_level." + next.id + ".share_pct", "value", next.sharePct));
        }
    }
}
