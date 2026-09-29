package kolo.engine.content;

import java.util.Locale;

/**
 * Вид стріку генерації (GD §4.10): кілька дуже добрих результатів поспіль ведуть до колеса «Золотої доби», дуже
 * поганих — до колеса «Андердога».
 *
 * <p>Enum, а не контент: видів рівно два, і лічильник стріків знає, який з них спрацьовує.
 */
public enum StreakKind {
    GOLDEN_AGE,
    UNDERDOG;

    /** Ключ у контенті, напр. {@code golden_age}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
