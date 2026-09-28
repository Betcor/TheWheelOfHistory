package kolo.content.loader;

import java.util.Locale;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.Stat;
import kolo.engine.wheel.WheelKind;

/** Ціль модифікатора в YAML: {@code stat:<показник>} або {@code wheel:<тип колеса>}. */
final class ModifierTargets {

    static final String STAT_PREFIX = "stat:";
    static final String WHEEL_PREFIX = "wheel:";

    private ModifierTargets() {}

    static ModifierTarget parse(String text) {
        if (text != null && text.startsWith(STAT_PREFIX)) {
            String key = text.substring(STAT_PREFIX.length());
            for (Stat stat : Stat.values()) {
                // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
                if (stat.name().toLowerCase(Locale.ROOT).equals(key)) {
                    return ModifierTarget.stat(stat);
                }
            }
        } else if (text != null && text.startsWith(WHEEL_PREFIX)) {
            return ModifierTarget.wheel(new WheelKind(text.substring(WHEEL_PREFIX.length())));
        }
        throw new ValidationException(
                ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", "modifier.target", "value", text));
    }
}
