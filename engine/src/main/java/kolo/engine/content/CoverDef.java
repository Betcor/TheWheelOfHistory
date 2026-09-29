package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Climate;
import kolo.engine.state.Cover;
import kolo.engine.state.Relief;

/**
 * Покрив у контенті (GD §3.5): назва, опис і умова, за якої клімат перетворює провінцію на цей покрив. Покриви
 * перевіряються в порядку контенту — провінція отримує перший, чия умова виконана, або жодного.
 *
 * @param name назва українською
 * @param description опис для енциклопедії й підказок
 * @param climates пояси, у яких буває покрив; непорожні, без повторів
 * @param reliefs рельєф, який покрив перетворює; непорожній, без повторів і без гір — гори покриву не мають
 * @param moisture волога провінції, у межах якої буває покрив, {@code 0..}{@value ClimateDef#MAX_VALUE}
 * @param height висота провінції, у межах якої буває покрив, {@code 0..}{@value ReliefDef#MAX_HEIGHT}
 */
public record CoverDef(
        Cover cover,
        String name,
        String description,
        List<Climate> climates,
        List<Relief> reliefs,
        CountRange moisture,
        CountRange height) {

    public CoverDef {
        Objects.requireNonNull(cover, "cover");
        String field = "cover." + cover.key();
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        climates = List.copyOf(Objects.requireNonNull(climates, field + ".climates"));
        reliefs = List.copyOf(Objects.requireNonNull(reliefs, field + ".reliefs"));
        if (climates.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field + ".climates"));
        }
        if (reliefs.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field + ".reliefs"));
        }
        Defs.uniqueAll(field + ".climates", climates);
        Defs.uniqueAll(field + ".reliefs", reliefs);
        if (reliefs.contains(Relief.MOUNTAINS)) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE,
                    ErrorDetails.of("field", field + ".reliefs", "value", Relief.MOUNTAINS.key()));
        }
        Objects.requireNonNull(moisture, field + ".moisture");
        Checks.inRange(field + ".moisture.max", moisture.max(), moisture.min(), ClimateDef.MAX_VALUE);
        Objects.requireNonNull(height, field + ".height");
        Checks.inRange(field + ".height.max", height.max(), height.min(), ReliefDef.MAX_HEIGHT);
    }

    /** Провінція з таким поясом, рельєфом, вологою й висотою може мати цей покрив. */
    public boolean matches(Climate climate, Relief relief, int moisture, int height) {
        return climates.contains(climate)
                && reliefs.contains(relief)
                && this.moisture.contains(moisture)
                && this.height.contains(height);
    }
}
