package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import kolo.engine.state.Climate;
import kolo.engine.state.Terrain;
import kolo.engine.view.CellKind;
import kolo.engine.view.CellView;
import org.junit.jupiter.api.Test;

class MapPaletteTest {

    @Test
    void terrainsAndClimatesAreDistinct() {
        assertThat(Arrays.stream(Terrain.values()).map(MapPalette::terrain).distinct())
                .hasSize(Terrain.values().length);
        assertThat(Arrays.stream(Climate.values()).map(MapPalette::climate).distinct())
                .hasSize(Climate.values().length);
    }

    @Test
    void countryColorsDifferForAllCountriesOfWorld() {
        Set<Integer> colors = new HashSet<>();
        for (int n = 0; n < 40; n++) {
            int color = MapPalette.country(n);
            assertThat(color).isNotEqualTo(MapPalette.SEA).isNotEqualTo(MapPalette.UNCLAIMED);
            colors.add(color);
        }
        assertThat(colors).hasSize(40);
    }

    @Test
    void fertilityGetsGreener() {
        int previous = Integer.MIN_VALUE;
        for (int fertility = 0; fertility <= 100; fertility += 10) {
            int green =
                    MapPalette.green(MapPalette.fertility(fertility)) - MapPalette.red(MapPalette.fertility(fertility));
            assertThat(green).isGreaterThan(previous);
            previous = green;
        }
        assertThat(MapPalette.fertility(-5)).isEqualTo(MapPalette.fertility(0));
        assertThat(MapPalette.fertility(500)).isEqualTo(MapPalette.fertility(100));
    }

    @Test
    void waterLooksTheSameInEveryMode() {
        for (MapMode mode : MapMode.values()) {
            assertThat(MapPalette.color(TestMaps.MAP.cells().get(2), mode)).isEqualTo(MapPalette.SEA);
            assertThat(MapPalette.color(TestMaps.MAP.cells().get(5), mode)).isEqualTo(MapPalette.LAKE);
        }
        assertThat(TestMaps.MAP.cells().get(5).kind()).isEqualTo(CellKind.LAKE);
    }

    @Test
    void landColorFollowsMode() {
        CellView forest = TestMaps.MAP.cells().get(1);
        assertThat(MapPalette.color(forest, MapMode.POLITICAL)).isEqualTo(MapPalette.country(1));
        assertThat(MapPalette.color(forest, MapMode.TERRAIN)).isEqualTo(MapPalette.terrain(Terrain.FOREST));
        assertThat(MapPalette.color(forest, MapMode.CLIMATE)).isEqualTo(MapPalette.climate(Climate.TEMPERATE));
        assertThat(MapPalette.color(forest, MapMode.FERTILITY)).isEqualTo(MapPalette.fertility(30));
        assertThat(MapPalette.color(TestMaps.MAP.cells().get(4), MapMode.POLITICAL))
                .isEqualTo(MapPalette.UNCLAIMED);
    }

    @Test
    void hsbAndChannels() {
        assertThat(MapPalette.hsb(0, 1, 1)).isEqualTo(MapPalette.rgb(255, 0, 0));
        assertThat(MapPalette.hsb(120, 1, 1)).isEqualTo(MapPalette.rgb(0, 255, 0));
        assertThat(MapPalette.hsb(240, 1, 1)).isEqualTo(MapPalette.rgb(0, 0, 255));
        assertThat(MapPalette.hsb(77, 0, 0.5)).isEqualTo(MapPalette.rgb(128, 128, 128));
        assertThat(MapPalette.darker(MapPalette.rgb(200, 100, 50), 0.5)).isEqualTo(MapPalette.rgb(100, 50, 25));
    }
}
