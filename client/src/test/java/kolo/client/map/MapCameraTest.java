package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class MapCameraTest {

    @Test
    void fitShowsWholeMapCentered() {
        MapCamera camera = new MapCamera(2000, 1000, 5);
        camera.resize(1000, 1000);

        assertThat(camera.scale()).isEqualTo(0.5);
        assertThat(camera.toScreenX(0)).isEqualTo(0);
        assertThat(camera.toScreenX(2000)).isEqualTo(1000);
        // По вертикалі карта (500 пікселів) менша за вікно — по центру.
        assertThat(camera.toScreenY(0)).isEqualTo(250);
        assertThat(camera.toScreenY(1000)).isEqualTo(750);
    }

    @Test
    void zoomKeepsPointUnderCursor() {
        MapCamera camera = new MapCamera(2000, 1000, 5);
        camera.resize(1000, 500);
        double u = camera.toMapX(300);
        double v = camera.toMapY(200);

        camera.zoom(2, 300, 200);

        assertThat(camera.scale()).isEqualTo(1);
        assertThat(camera.toScreenX(u)).isCloseTo(300, within(1e-9));
        assertThat(camera.toScreenY(v)).isCloseTo(200, within(1e-9));
    }

    @Test
    void scaleIsClamped() {
        MapCamera camera = new MapCamera(2000, 1000, 2);
        camera.resize(1000, 500);

        camera.zoom(1000, 0, 0);
        assertThat(camera.scale()).isEqualTo(2);
        camera.zoom(1e-6, 0, 0);
        assertThat(camera.scale()).isEqualTo(camera.minScale()).isEqualTo(0.5);
    }

    @Test
    void panStopsAtMapEdge() {
        MapCamera camera = new MapCamera(2000, 1000, 4);
        camera.resize(1000, 500);
        camera.zoom(4, 0, 0);

        camera.pan(10_000, 10_000);
        assertThat(camera.left()).isEqualTo(0);
        assertThat(camera.top()).isEqualTo(0);
        camera.pan(-100_000, -100_000);
        assertThat(camera.right()).isCloseTo(2000, within(1e-9));
        assertThat(camera.bottom()).isCloseTo(1000, within(1e-9));
    }

    @Test
    void resizeKeepsCenter() {
        MapCamera camera = new MapCamera(2000, 1000, 4);
        camera.resize(1000, 500);
        camera.zoom(4, 700, 100);
        double u = camera.toMapX(500);
        double v = camera.toMapY(250);

        camera.resize(800, 400);

        assertThat(camera.toMapX(400)).isCloseTo(u, within(1e-9));
        assertThat(camera.toMapY(200)).isCloseTo(v, within(1e-9));
    }

    @Test
    void screenAndMapAreInverse() {
        MapCamera camera = new MapCamera(3000, 1500, 3);
        camera.resize(1280, 800);
        camera.zoom(3.7, 100, 600);

        assertThat(camera.toMapX(camera.toScreenX(1234.5))).isCloseTo(1234.5, within(1e-9));
        assertThat(camera.toMapY(camera.toScreenY(777.25))).isCloseTo(777.25, within(1e-9));
    }

    @Test
    void rejectsEmptyMap() {
        assertThatThrownBy(() -> new MapCamera(0, 10, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MapCamera(10, 10, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
