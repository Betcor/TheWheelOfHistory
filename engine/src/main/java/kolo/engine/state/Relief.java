package kolo.engine.state;

import java.util.Locale;

/**
 * Рельєф провінції за висотою (GD §3.5). Ліс, пустеля, тундра й болото — покрив, що залежить від клімату, і
 * визначається окремо; рельєф лишається.
 */
public enum Relief {
    PLAIN,
    HILLS,
    MOUNTAINS;

    /** Ключ у контенті, напр. {@code mountains}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
