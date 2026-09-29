package kolo.engine.generation.map;

import kolo.engine.content.ContentPack;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: розмір світу задає кількість комірок, сітка з контенту вкриває карту. */
class VoronoiGridSmokeTest {

    @Test
    void worldSizeGivesGrid() {
        ContentPack pack = TestNames.PACK;
        Rng rng = Rng.of(1970);
        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), pack, WorldSizeInput.of(2, NpcShare.FEW));
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), pack.map().grid(), size.provinces());

        GridChecks.assertValid(grid, size.provinces());
    }
}
