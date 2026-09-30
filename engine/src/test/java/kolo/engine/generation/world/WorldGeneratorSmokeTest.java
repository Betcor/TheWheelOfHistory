package kolo.engine.generation.world;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: зі seed-а виходить цілий світ — карта, релігії, держави й святі центри. */
class WorldGeneratorSmokeTest {

    @Test
    void generatesWorld() {
        StartWorld world = WorldGenerator.generate(Rng.of(1970), TestNames.PACK, WorldSizeInput.of(1, NpcShare.FEW));

        assertThat(world.countries()).hasSize(world.map().countries()).isNotEmpty();
        assertThat(world.religions().religions()).isNotEmpty();
        assertThat(world.holyCenters().centers())
                .hasSize(world.religions().religions().size());
    }
}
