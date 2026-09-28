package kolo.engine.state;

import java.util.Locale;

/**
 * Стать постаті. Впливає лише на текст: рід імені для узгодження в хроніці («генерал Велор наказав», «Велена
 * наказала») і відмінювання прізвища («Ольга Торвер» не відмінюється, «Олег Торвер» — відмінюється).
 */
public enum Sex {
    MALE,
    FEMALE;

    /** Ключ у контенті, напр. {@code female}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }

    /** Рід імені людини цієї статі. */
    public GrammaticalGender gender() {
        return switch (this) {
            case MALE -> GrammaticalGender.MASCULINE;
            case FEMALE -> GrammaticalGender.FEMININE;
        };
    }
}
