package kolo.engine.state;

import java.util.Locale;

/**
 * Кліматичний пояс провінції (GD §3.5): залежить від температури (широта, висота, клімат світу) і вологи (відстань
 * від моря). Клімат перетворює рівнини й пагорби на покрив ({@link Cover}) і впливатиме на сезонні штрафи.
 */
public enum Climate {
    POLAR,
    BOREAL,
    TEMPERATE,
    ARID,
    TROPICAL;

    /** Ключ у контенті, напр. {@code boreal}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
