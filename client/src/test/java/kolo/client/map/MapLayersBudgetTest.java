package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalInt;
import kolo.client.Budget;
import kolo.client.TestWorlds;
import kolo.engine.view.MapView;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Бюджет підготовки карти на найбільшому світі: шари будуються раз після генерації світу, кольори — при кожній зміні
 * режиму, хіт-тест — на кожен рух миші.
 */
@Tag("budget")
class MapLayersBudgetTest {

    private static final MapView VIEW = TestWorlds.largest(1970);

    @Test
    void layersAreQuick() {
        Budget.Timed<MapLayers> build = Budget.best(() -> MapLayers.build(VIEW));
        Budget.Timed<List<int[]>> paint = Budget.best(() -> build.result().paint(MapMode.POLITICAL));
        MapHitTest hit = build.result().hitTest();
        Budget.Timed<Integer> queries = Budget.best(() -> {
            int found = 0;
            for (int i = 0; i < 100_000; i++) {
                OptionalInt cell = hit.cellAt(
                        (i * 7919L % 100_000) / 100_000.0 * VIEW.width(),
                        (i * 104_729L % 100_000) / 100_000.0 * VIEW.height());
                found += cell.isPresent() ? 1 : 0;
            }
            return found;
        });
        System.out.printf(
                "cells %d, build %d ms, paint %d ms, 100k hits %d ms%n",
                VIEW.cells().size(), build.millis(), paint.millis(), queries.millis());

        assertThat(build.millis()).isLessThan(500);
        assertThat(paint.millis()).isLessThan(300);
        assertThat(queries.millis()).isLessThan(100);
        assertThat(queries.result()).isEqualTo(100_000);
    }
}
