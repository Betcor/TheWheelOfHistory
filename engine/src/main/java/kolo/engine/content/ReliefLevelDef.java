package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Relief;

/**
 * Рівень рельєфу в контенті (GD §3.5): назва й висота, з якої він починається.
 *
 * @param name назва українською
 * @param description опис для енциклопедії й підказок
 * @param minHeight найменша висота провінції з цим рельєфом, {@code 0..}{@value ReliefDef#MAX_HEIGHT}; у рівнини — 0
 */
public record ReliefLevelDef(Relief relief, String name, String description, int minHeight) {

    public ReliefLevelDef {
        Objects.requireNonNull(relief, "relief");
        Checks.notBlank("relief." + relief.key() + ".name", name);
        Checks.notBlank("relief." + relief.key() + ".description", description);
        Checks.inRange("relief." + relief.key() + ".min_height", minHeight, 0, ReliefDef.MAX_HEIGHT);
    }

    /**
     * {@code next} іде за {@code previous} у порядку {@link Relief}: вищий рельєф починається строго вище.
     *
     * @throws ValidationException з {@link ErrorCode#OUT_OF_ORDER}
     */
    public static void checkFollows(ReliefLevelDef previous, ReliefLevelDef next) {
        if (next.minHeight <= previous.minHeight) {
            throw new ValidationException(
                    ErrorCode.OUT_OF_ORDER,
                    ErrorDetails.of("field", "relief." + next.relief.key() + ".min_height", "value", next.minHeight));
        }
    }
}
