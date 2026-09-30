package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.TestResources;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: розмір світу → … → річки → родючість → придатність до родовищ. */
class ResourceSuitabilityGeneratorSmokeTest {

    @Test
    void worldSizeGivesResourceSuitability() {
        ContentPack pack = TestNames.pack(TestResources.RESOURCES, TestResources.BALANCE);
        Rng rng = Rng.of(1970);
        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), pack, WorldSizeInput.of(2, NpcShare.FEW));
        MapTemplateDef template = pack.map().template(size.template()).orElseThrow();
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), pack.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(rng.fork("continents"), pack, size, grid);
        ReliefMap relief = ReliefGenerator.generate(rng.fork("relief"), pack, grid, continents);
        ClimateMap climate = ClimateGenerator.generate(rng.fork("climate"), pack, grid, continents, relief);
        SeaMap sea = SeaGenerator.generate(rng.fork("sea"), pack, grid, continents);
        RiverMap rivers = RiverGenerator.generate(pack, grid, relief, climate, sea);
        FertilityMap fertility = FertilityGenerator.generate(pack, climate, rivers);

        ResourceSuitabilityMap suitability = ResourceSuitabilityGenerator.generate(pack, climate, fertility);

        ResourceSuitabilityChecks.assertValid(pack, climate, fertility, suitability);
        assertThat(suitability.suitabilities()).hasSize(size.provinces());
    }
}
