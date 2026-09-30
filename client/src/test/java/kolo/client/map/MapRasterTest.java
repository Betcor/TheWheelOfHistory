package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import kolo.engine.state.Terrain;
import org.junit.jupiter.api.Test;

class MapRasterTest {

    private static final MapGeometry GEOMETRY = MapGeometry.of(TestMaps.MAP);
    /** Піксель на одиницю карти: комірка — 10 × 10 пікселів. */
    private static final RasterLevel LEVEL = new RasterLevel(30, 20, 1);

    @Test
    void everyPixelBelongsToCellUnderItsCenter() {
        int[] ids = MapRaster.cellIds(GEOMETRY, LEVEL);
        MapHitTest hit = new MapHitTest(GEOMETRY, 10);

        for (int y = 0; y < LEVEL.height(); y++) {
            for (int x = 0; x < LEVEL.width(); x++) {
                assertThat(ids[y * LEVEL.width() + x])
                        .as("pixel %d,%d", x, y)
                        .isEqualTo(hit.cellAt(x + 0.5, y + 0.5).getAsInt());
            }
        }
        // Нижній рядок екрана — рядок 0 рушія.
        assertThat(ids[19 * 30]).isZero();
        assertThat(ids[0]).isEqualTo(3);
    }

    @Test
    void coarseRasterStillCoversEveryPixel() {
        RasterLevel coarse = new RasterLevel(7, 5, 7.0 / 30);
        int[] ids = MapRaster.cellIds(GEOMETRY, coarse);

        assertThat(IntStream.of(ids)).allSatisfy(id -> assertThat(id).isBetween(0, GEOMETRY.cells() - 1));
    }

    @Test
    void fillsAndBorders() {
        int[] ids = MapRaster.cellIds(GEOMETRY, LEVEL);
        int[] argb = MapRaster.paint(TestMaps.MAP, GEOMETRY, LEVEL, ids, MapMode.POLITICAL, true, 1);

        // Усередині комірок — заливка режиму.
        assertThat(pixel(argb, 2, 12)).isEqualTo(MapPalette.country(0));
        assertThat(pixel(argb, 25, 12)).isEqualTo(MapPalette.SEA);
        assertThat(pixel(argb, 25, 2)).isEqualTo(MapPalette.LAKE);
        assertThat(pixel(argb, 12, 2)).isEqualTo(MapPalette.UNCLAIMED);
        // Кордон держав 0 і 1 — тонкий, з боку лівої комірки.
        assertThat(pixel(argb, 9, 17)).isEqualTo(MapPalette.COUNTRY_BORDER);
        assertThat(pixel(argb, 10, 17)).isEqualTo(MapPalette.country(1));
        // Межа провінцій однієї держави (3 над 0) — трохи темніша заливка.
        assertThat(pixel(argb, 2, 9)).isEqualTo(MapPalette.darker(MapPalette.country(0), 0.86));
        // Берег — з боку суходолу; вода не затемнюється.
        assertThat(pixel(argb, 19, 12)).isEqualTo(MapPalette.darker(MapPalette.country(1), 0.68));
        assertThat(pixel(argb, 20, 12)).isEqualTo(MapPalette.SEA);
        // Річка від центру провінції 1 до берега моря.
        assertThat(pixel(argb, 15, 15)).isEqualTo(MapPalette.RIVER);
        assertThat(pixel(argb, 17, 15)).isEqualTo(MapPalette.RIVER);
    }

    @Test
    void thickBordersAndNoProvinceBorders() {
        int[] ids = MapRaster.cellIds(GEOMETRY, LEVEL);
        int[] argb = MapRaster.paint(TestMaps.MAP, GEOMETRY, LEVEL, ids, MapMode.TERRAIN, false, 2);

        // Кордон держав — з обох боків, у неполітичних режимах — затемнена заливка.
        assertThat(pixel(argb, 9, 17)).isEqualTo(MapPalette.darker(MapPalette.terrain(Terrain.PLAIN), 0.5));
        assertThat(pixel(argb, 10, 17)).isEqualTo(MapPalette.darker(MapPalette.terrain(Terrain.FOREST), 0.5));
        // Межі провінцій вимкнено.
        assertThat(pixel(argb, 2, 9)).isEqualTo(MapPalette.terrain(Terrain.MOUNTAINS));
    }

    @Test
    void lineClipsToRaster() {
        int[] argb = new int[4 * 3];

        MapRaster.line(argb, 4, 3, -5, 1.5, 10, 1.5, 1, 7);

        assertThat(argb).containsExactly(0, 0, 0, 0, 7, 7, 7, 7, 0, 0, 0, 0);
    }

    private static int pixel(int[] argb, int x, int y) {
        return argb[y * LEVEL.width() + x];
    }
}
