package kolo.engine.state;

import java.util.Locale;

/** Відмінок української мови: назви держав і людей зберігаються в усіх семи формах (GD §19.1). */
public enum GrammaticalCase {
    NOMINATIVE,
    GENITIVE,
    DATIVE,
    ACCUSATIVE,
    INSTRUMENTAL,
    /** Місцевий, без прийменника: «(у) Велорі». */
    LOCATIVE,
    VOCATIVE;

    /** Ключ у контенті, напр. {@code instrumental}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
