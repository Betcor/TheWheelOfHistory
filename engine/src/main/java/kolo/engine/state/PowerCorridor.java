package kolo.engine.state;

import java.util.Locale;

/**
 * Ширина коридору бюджету сили при генерації країн (GD §4.11) — параметр сесії, який задає хост (GD §3.4).
 *
 * <p>Enum, а не контент: варіанти показує лобі; межі кожного варіанта — у балансі.
 */
public enum PowerCorridor {
    /** «Рівні шанси»: найвужчий коридор. */
    EQUAL_CHANCES,
    /** «Класика». */
    CLASSIC,
    /** «Повний хаос»: найширший коридор. */
    FULL_CHAOS;

    /** Ключ у контенті, напр. {@code full_chaos}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
