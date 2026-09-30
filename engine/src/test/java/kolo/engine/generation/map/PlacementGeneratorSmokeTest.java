package kolo.engine.generation.map;

import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: розмір світу → сітка → материки → держави на карті. */
class PlacementGeneratorSmokeTest {

    @Test
    void worldSizeGivesPlacedCountries() {
        ContentPack pack = TestNames.PACK;
        Rng rng = Rng.of(1970);
        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), pack, WorldSizeInput.of(3, NpcShare.NORMAL));
        MapTemplateDef template = pack.map().template(size.template()).orElseThrow();
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), pack.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(rng.fork("continents"), pack, size, grid);

        PlacementMap placement = PlacementGenerator.generate(rng.fork("placement"), pack, size, grid, continents);

        PlacementChecks.assertValid(grid, continents, placement, size, TestMaps.PLACEMENT.minProvinces());
    }
}
