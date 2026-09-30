package kolo.engine.generation.world;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldInvariants;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;

/** Смок: світ генерується, стає станом, проходить інваріанти й копіюється. */
class WorldStatesSmokeTest {

    @Test
    void generatedWorldBecomesAValidState() {
        StartWorld world = WorldGenerator.generate(Rng.of(42), TestNames.PACK, WorldSizeInput.of(1, NpcShare.NORMAL));
        WorldState state = WorldStates.of(42, TestNames.PACK, world);

        WorldInvariants.check(state);
        assertThat(state.countries()).hasSize(world.countries().size());
        assertThat(state.deepCopy()).isEqualTo(state);
    }
}
