package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.OutcomeTier;

/**
 * Рівень розміру армії (GD §4.1, колесо 10; GD §4.4) і його сектор у колесі розміру армії.
 *
 * <p>Рівні йдуть від найменшої армії до найбільшої ({@link #checkFollows}): це порядок секторів, а рівень результату
 * визначає, в який бік їх зсуває перевага.
 *
 * @param name назва українською, напр. «Звичайна армія»
 * @param description що означає рівень, для підказки гравцеві
 * @param shareBp частка населення під зброєю, базисні пункти {@code 1..}{@value #MAX_SHARE_BP}; чисельність армії —
 *     частка × населення, разом із картою
 * @param tier як на рівень діє перевага: менші армії — провали, більші — успіхи; крайні рівні зазвичай критичні,
 *     тоді вони не бувають рідше 1%
 * @param weight базова вага в колесі розміру армії, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує
 *     ваги всіх рівнів, а перевага зсуває їх
 * @param quality наскільки рівень добрий для держави, {@code 0..100} (стріки генерації)
 * @param tags мітки, які рівень дає державі, — вхід для наступних коліс
 */
public record ArmySizeDef(
        ArmySizeId id,
        String name,
        String description,
        int shareBp,
        OutcomeTier tier,
        int weight,
        int quality,
        List<String> tags) {

    /** Під зброєю не може бути більше за все населення. */
    public static final int MAX_SHARE_BP = 10_000;

    /** Найбільша відносна вага рівня в колесі розміру армії. */
    public static final int MAX_WEIGHT = 10_000;

    public ArmySizeDef {
        Objects.requireNonNull(id, "army_size.id");
        Checks.notBlank("army_size." + id + ".name", name);
        Checks.notBlank("army_size." + id + ".description", description);
        Checks.inRange("army_size." + id + ".share_bp", shareBp, 1, MAX_SHARE_BP);
        Objects.requireNonNull(tier, "army_size." + id + ".tier");
        Checks.inRange("army_size." + id + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange("army_size." + id + ".quality", quality, 0, 100);
        tags = Defs.tags("army_size." + id + ".tags", tags);
    }

    /**
     * {@code next} іде за {@code previous}: частка строго більша, а рівень результату не нижчий. Інакше додатна
     * перевага могла б зсувати шанси до меншої армії.
     *
     * @throws ValidationException з {@link ErrorCode#OUT_OF_ORDER}
     */
    public static void checkFollows(ArmySizeDef previous, ArmySizeDef next) {
        if (next.shareBp <= previous.shareBp) {
            throw outOfOrder("army_size." + next.id + ".share_bp", next.shareBp);
        }
        if (next.tier.compareTo(previous.tier) < 0) {
            throw outOfOrder("army_size." + next.id + ".tier", next.tier.key());
        }
    }

    private static ValidationException outOfOrder(String field, Object value) {
        return new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", field, "value", value));
    }
}
