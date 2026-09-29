package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: розмір світу → сітка з морем за шаблоном → материки → море й морські зони. */
class SeaGeneratorSmokeTest {

    @Test
    void worldSizeGivesSeaZones() {
        ContentPack pack = TestNames.PACK;
        Rng rng = Rng.of(1970);
        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), pack, WorldSizeInput.of(2, NpcShare.FEW));
        MapTemplateDef template = pack.map().template(size.template()).orElseThrow();
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), pack.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(rng.fork("continents"), pack, size, grid);

        SeaMap sea = SeaGenerator.generate(rng.fork("sea"), pack, grid, continents);

        SeaChecks.assertValid(grid, continents, sea, pack.map().sea());
        assertThat(sea.zones()).isNotEmpty();
        assertThat(sea.coasts()).isNotEmpty();
    }
}
