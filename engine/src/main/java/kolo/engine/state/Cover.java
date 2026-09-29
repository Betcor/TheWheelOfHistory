package kolo.engine.state;

import java.util.Locale;

/**
 * Покрив провінції (GD §3.5): клімат перетворює рівнини й пагорби на ліс, пустелю, тундру чи болото, а висота й
 * рельєф лишаються. Гори покриву не мають.
 */
public enum Cover {
    FOREST,
    DESERT,
    TUNDRA,
    SWAMP;

    /** Ключ у контенті, напр. {@code swamp}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }

    /** Тип місцевості провінції з цим покривом. */
    public Terrain terrain() {
        return switch (this) {
            case FOREST -> Terrain.FOREST;
            case DESERT -> Terrain.DESERT;
            case TUNDRA -> Terrain.TUNDRA;
            case SWAMP -> Terrain.SWAMP;
        };
    }
}
