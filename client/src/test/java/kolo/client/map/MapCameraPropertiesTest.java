package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

class MapCameraPropertiesTest {

    private static final double EPSILON = 1e-6;

    @Property
    void cameraStaysInsideLimits(
            @ForAll @IntRange(min = 100, max = 20_000) int mapWidth,
            @ForAll @IntRange(min = 100, max = 20_000) int mapHeight,
            @ForAll @IntRange(min = 200, max = 3000) int viewWidth,
            @ForAll @IntRange(min = 200, max = 3000) int viewHeight,
            @ForAll @DoubleRange(min = 0.01, max = 100) double zoom,
            @ForAll @DoubleRange(min = -5000, max = 5000) double dx,
            @ForAll @DoubleRange(min = -5000, max = 5000) double dy) {
        MapCamera camera = new MapCamera(mapWidth, mapHeight, 2);
        camera.resize(viewWidth, viewHeight);
        camera.zoom(zoom, viewWidth / 3.0, viewHeight / 2.0);
        camera.pan(dx, dy);

        assertThat(camera.scale()).isBetween(camera.minScale(), camera.maxScale());
        checkAxis(camera.left(), camera.right(), mapWidth);
        checkAxis(camera.top(), camera.bottom(), mapHeight);
    }

    @Property
    void zoomInsideLimitsKeepsPointUnderCursor(
            @ForAll @IntRange(min = 0, max = 1280) int x,
            @ForAll @IntRange(min = 0, max = 800) int y,
            @ForAll @DoubleRange(min = 1.01, max = 3) double factor) {
        MapCamera camera = new MapCamera(16_000, 8000, 2);
        camera.resize(1280, 800);
        camera.zoom(2, 640, 400);
        double u = camera.toMapX(x);
        double v = camera.toMapY(y);

        camera.zoom(factor, x, y);

        // Наближення не впирається в край карти: точка під курсором завжди лишається всередині карти.
        assertThat(Math.abs(camera.toScreenX(u) - x)).isLessThan(EPSILON);
        assertThat(Math.abs(camera.toScreenY(v) - y)).isLessThan(EPSILON);
    }

    private static void checkAxis(double start, double end, double size) {
        if (end - start >= size) {
            // Карта менша за вікно — по центру.
            assertThat(Math.abs((start + end) / 2 - size / 2)).isLessThan(EPSILON);
        } else {
            assertThat(start).isGreaterThanOrEqualTo(-EPSILON);
            assertThat(end).isLessThanOrEqualTo(size + EPSILON);
        }
    }
}
