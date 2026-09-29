package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: розмір світу визначає, скільки держав отримають релігії світу. */
class WorldSizeWheelSmokeTest {

    @Test
    void worldSizeFeedsWorldReligions() {
        ContentPack pack = TestNames.PACK;
        Rng rng = Rng.of(1970);
        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), pack, WorldSizeInput.of(4, NpcShare.NORMAL));
        StartReligions religions = WorldReligionsWheel.generate(rng.fork("religions"), pack, size.countries());

        assertThat(size.countries()).isBetween(4 + 4 + 2, 4 + 4 + 4);
        assertThat(size.provinces()).isPositive();
        assertThat(religions.religions()).isNotEmpty();
    }
}
