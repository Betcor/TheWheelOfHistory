package kolo.engine.state;

import java.util.Locale;

/**
 * Тип відомої постаті (GD §12.3).
 *
 * <p>Enum, а не контент: від типу залежать посади й дії постаті (генерал командує фронтом, вчений очолює НДІ).
 */
public enum PersonKind {
    SCIENTIST,
    GENERAL,
    ADMIRAL,
    DIPLOMAT,
    MAGNATE,
    PROPHET,
    DISSIDENT,
    ARTIST,
    /** Диктатор-претендент. */
    PRETENDER;

    /** Ключ у контенті, напр. {@code pretender}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
