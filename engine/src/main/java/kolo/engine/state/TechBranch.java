package kolo.engine.state;

import java.util.Locale;

/**
 * Галузь технологій (GD §9.1): гілка дерева технологій і вісь стартової розвиненості (GD §4.3).
 *
 * <p>Enum, а не контент: чотири галузі — частина правил, на них спираються системи науки й генератор країн.
 */
public enum TechBranch {
    ECONOMY,
    MILITARY,
    SOCIETY,
    ENERGY_SCIENCE;

    /** Ключ у контенті, напр. {@code energy_science}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
