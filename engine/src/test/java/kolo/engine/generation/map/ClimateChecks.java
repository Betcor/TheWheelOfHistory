package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import kolo.engine.content.ClimateDef;
import kolo.engine.state.Cover;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;

/** Перевірки клімату, спільні для тестів рушія. */
public final class ClimateChecks {

    private ClimateChecks() {}

    /**
     * Клімат має рівно суходіл; температура й волога — у шкалі; пояс, покрив і тип місцевості відповідають порогам і
     * умовам контенту; гори без покриву; клімат світу — результат колеса.
     */
    public static void assertValid(ContinentMap continents, ReliefMap relief, ClimateMap climate, ClimateDef def) {
        assertThat(climate.temperatures().keySet()).isEqualTo(relief.heights().keySet());
        assertThat(climate.temperatures()).hasSize(continents.landCells());
        for (int cell : climate.temperatures().keySet()) {
            int temperature = climate.temperatures().get(cell);
            int moisture = climate.moistures().get(cell);
            int height = relief.heights().get(cell);
            assertThat(temperature).isBetween(0, ClimateDef.MAX_VALUE);
            assertThat(moisture).isBetween(0, ClimateDef.MAX_VALUE);
            assertThat(climate.climates().get(cell)).isEqualTo(def.climate(temperature, moisture));
            Optional<Cover> cover =
                    def.cover(climate.climates().get(cell), relief.reliefs().get(cell), moisture, height);
            assertThat(climate.cover(cell)).isEqualTo(cover);
            assertThat(climate.terrains().get(cell))
                    .isEqualTo(Terrain.of(relief.reliefs().get(cell), cover));
        }
        assertThat(climate.count(Terrain.MOUNTAINS)).isEqualTo(relief.count(Relief.MOUNTAINS));
        assertThat(climate.roll().kind()).isEqualTo(ClimateGenerator.WORLD_KIND);
        assertThat(climate.roll().resultSectorId()).isEqualTo(climate.world().value());
        assertThat(def.worlds()).anySatisfy(world -> assertThat(world.id()).isEqualTo(climate.world()));
    }
}
