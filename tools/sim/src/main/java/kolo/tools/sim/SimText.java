package kolo.tools.sim;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Тексти CLI з {@code i18n/sim_uk.properties}.
 *
 * <p>Підстановка {@code {0}}, {@code {1}}… — простою заміною, а не {@link java.text.MessageFormat}: той ковтає
 * апострофи, а в українських текстах їх багато («ім'я»).
 */
final class SimText {

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "i18n.sim",
            Locale.of("uk"),
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));

    private SimText() {}

    static String text(String key, Object... args) {
        String result = BUNDLE.getString(key);
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }
}
