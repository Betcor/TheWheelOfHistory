package kolo.engine.generation.map;

import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: розмір світу → сітка з морем за шаблоном → материки. */
class ContinentGeneratorSmokeTest {

    @Test
    void worldSizeGivesContinents() {
        ContentPack pack = TestNames.PACK;
        Rng rng = Rng.of(1970);
        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), pack, WorldSizeInput.of(2, NpcShare.FEW));
        MapTemplateDef template = pack.map().template(size.template()).orElseThrow();
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), pack.map().grid(), template.gridCells(size.provinces()));

        ContinentMap map = ContinentGenerator.generate(rng.fork("continents"), pack, size, grid);

        ContinentChecks.assertValid(grid, map, size, TestMaps.CONTINENTS.minProvinces());
    }
}
