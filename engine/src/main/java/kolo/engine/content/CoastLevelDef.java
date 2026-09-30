package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Рівень виходу держави до моря (GD §4.1, № 3 — географія): за часткою її провінцій, що межують з морем.
 *
 * <p>Рівні йдуть від найменшої частки до найбільшої ({@link #checkFollows}); держава отримує останній рівень, чия
 * {@code minPct} не більша за її частку. Частка округлюється вгору, тож одна прибережна провінція серед сотні вже
 * дає 1%, а 0% — лише держава без виходу до моря.
 *
 * @param name назва українською, напр. «Морська держава»
 * @param description що означає рівень, для підказки гравцеві
 * @param minPct з якої частки прибережних провінцій діє рівень, {@code 0..100} %; перший рівень — з нуля
 * @param tags мітки, які рівень дає державі (напр. {@code landlocked}), — вхід для наступних коліс
 */
public record CoastLevelDef(CoastLevelId id, String name, String description, int minPct, List<String> tags) {

    public CoastLevelDef {
        Objects.requireNonNull(id, "coast_level.id");
        Checks.notBlank("coast_level." + id + ".name", name);
        Checks.notBlank("coast_level." + id + ".description", description);
        Checks.inRange("coast_level." + id + ".min_pct", minPct, 0, 100);
        tags = Defs.tags("coast_level." + id + ".tags", tags);
    }

    /**
     * {@code next} іде за {@code previous}: поріг строго більший.
     *
     * @throws ValidationException з {@link ErrorCode#OUT_OF_ORDER}
     */
    public static void checkFollows(CoastLevelDef previous, CoastLevelDef next) {
        if (next.minPct <= previous.minPct) {
            throw new ValidationException(
                    ErrorCode.OUT_OF_ORDER,
                    ErrorDetails.of("field", "coast_level." + next.id + ".min_pct", "value", next.minPct));
        }
    }
}
