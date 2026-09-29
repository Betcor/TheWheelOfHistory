package kolo.engine.state;

import java.util.Locale;
import java.util.Optional;

/**
 * Тип місцевості провінції (GD §3.5) — один із семи: рельєф, якщо покриву немає, інакше покрив. Саме його бачать
 * бої, доктрини й карта; висота й рельєф під покривом зберігаються окремо.
 */
public enum Terrain {
    PLAIN,
    HILLS,
    MOUNTAINS,
    FOREST,
    DESERT,
    TUNDRA,
    SWAMP;

    /** Ключ у контенті, напр. {@code mountains}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }

    /** Покрив, якщо він є, інакше рельєф. */
    public static Terrain of(Relief relief, Optional<Cover> cover) {
        if (cover.isPresent()) {
            return cover.get().terrain();
        }
        return switch (relief) {
            case PLAIN -> PLAIN;
            case HILLS -> HILLS;
            case MOUNTAINS -> MOUNTAINS;
        };
    }
}
