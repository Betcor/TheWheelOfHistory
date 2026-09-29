package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: розмір світу → сітка з морем за шаблоном → материки → рельєф → клімат. */
class ClimateGeneratorSmokeTest {

    @Test
    void worldSizeGivesClimate() {
        ContentPack pack = TestNames.PACK;
        Rng rng = Rng.of(1970);
        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), pack, WorldSizeInput.of(2, NpcShare.FEW));
        MapTemplateDef template = pack.map().template(size.template()).orElseThrow();
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), pack.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(rng.fork("continents"), pack, size, grid);
        ReliefMap relief = ReliefGenerator.generate(rng.fork("relief"), pack, grid, continents);

        ClimateMap climate = ClimateGenerator.generate(rng.fork("climate"), pack, grid, continents, relief);

        ClimateChecks.assertValid(continents, relief, climate, pack.map().climate());
        assertThat(climate.terrains()).hasSize(size.provinces());
    }
}
