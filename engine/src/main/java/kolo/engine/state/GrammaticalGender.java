package kolo.engine.state;

import java.util.Locale;

/**
 * Рід (або множина) словосполучення: з ним узгоджуються дієслова й прикметники в шаблонах хроніки («Велор оголосив»,
 * «Республіка Велор оголосила», «Об'єднані Провінції Велор оголосили»).
 */
public enum GrammaticalGender {
    MASCULINE,
    FEMININE,
    NEUTER,
    PLURAL;

    /** Ключ у контенті, напр. {@code feminine}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
