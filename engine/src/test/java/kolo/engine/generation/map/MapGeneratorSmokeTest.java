package kolo.engine.generation.map;

import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: з одного seed — уся карта з державами, кожен етап узгоджений з попередніми. */
class MapGeneratorSmokeTest {

    @Test
    void generatesWholeMap() {
        ContentPack pack = TestNames.PACK;

        WorldMap map = MapGenerator.generate(Rng.of(1970), pack, WorldSizeInput.of(2, NpcShare.NORMAL));

        GridChecks.assertValid(map.grid(), map.grid().cells().size());
        ContinentChecks.assertValid(map.grid(), map.continents(), map.size(), TestMaps.CONTINENTS.minProvinces());
        ReliefChecks.assertValid(
                map.grid(), map.continents(), map.relief(), pack.map().relief());
        ClimateChecks.assertValid(
                map.continents(), map.relief(), map.climate(), pack.map().climate());
        SeaChecks.assertValid(
                map.grid(), map.continents(), map.sea(), pack.map().sea());
        RiverChecks.assertValid(
                map.grid(),
                map.relief(),
                map.climate(),
                map.sea(),
                map.rivers(),
                pack.map().rivers());
        FertilityChecks.assertValid(
                map.climate(), map.rivers(), map.fertility(), pack.map().fertility());
        ResourceSuitabilityChecks.assertValid(pack, map.climate(), map.fertility(), map.suitability());
        PlacementChecks.assertValid(
                map.grid(), map.continents(), map.placement(), map.size(), TestMaps.PLACEMENT.minProvinces());
    }
}
