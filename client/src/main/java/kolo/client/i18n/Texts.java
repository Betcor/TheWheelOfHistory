package kolo.client.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import kolo.engine.error.ErrorCode;

/**
 * Тексти інтерфейсу з {@code i18n/messages_uk.properties}.
 *
 * <p>Підстановка {@code {0}}, {@code {1}}… — простою заміною, а не {@link java.text.MessageFormat}: той ковтає
 * апострофи, а в українських текстах їх багато («ім'я»).
 */
public final class Texts {

    private final ResourceBundle bundle;

    private Texts(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    /** Українські тексти гри. */
    public static Texts ukrainian() {
        return new Texts(ResourceBundle.getBundle(
                "i18n.messages",
                Locale.of("uk"),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)));
    }

    /** @throws java.util.MissingResourceException якщо ключа немає — це баг клієнта, а не гравця */
    public String text(String key, Object... args) {
        String result = bundle.getString(Objects.requireNonNull(key, "key"));
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }

    /**
     * Текст помилки для гравця за кодом; {@code {назва}} замінюється подробицею з тією назвою. Тексти помилок
     * підставляють лише обов'язкові подробиці коду ({@link ErrorCode#requiredDetails()}), тож заміна повна; решта
     * подробиць — для розробника.
     */
    public String error(ErrorCode code, Map<String, ?> details) {
        String result = text(code.key());
        for (Map.Entry<String, ?> detail : details.entrySet()) {
            result = result.replace("{" + detail.getKey() + "}", String.valueOf(detail.getValue()));
        }
        return result;
    }

    /** Чи є текст із таким ключем. */
    public boolean has(String key) {
        return bundle.containsKey(key);
    }
}
