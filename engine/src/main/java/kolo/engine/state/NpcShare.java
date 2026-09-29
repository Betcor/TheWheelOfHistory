package kolo.engine.state;

import java.util.Locale;

/**
 * Частка NPC-держав у світі (GD §14.4) — параметр сесії, який задає хост. Кожен варіант має свій діапазон колеса
 * кількості NPC у балансі.
 */
public enum NpcShare {
    /** «Мало». */
    FEW,
    /** «Звичайно». */
    NORMAL,
    /** «Багато». */
    MANY;

    /** Ключ у контенті, напр. {@code normal}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
