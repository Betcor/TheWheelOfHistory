package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.generation.map.ClimateGenerator;
import kolo.engine.generation.map.ClimateMap;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.FertilityGenerator;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.ReliefGenerator;
import kolo.engine.generation.map.ReliefMap;
import kolo.engine.generation.map.ResourceSuitabilityGenerator;
import kolo.engine.generation.map.ResourceSuitabilityMap;
import kolo.engine.generation.map.RiverGenerator;
import kolo.engine.generation.map.RiverMap;
import kolo.engine.generation.map.SeaGenerator;
import kolo.engine.generation.map.SeaMap;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/** Основний сценарій: карта → придатність до родовищ → колесо ресурсів держави на першому материку. */
class ResourceWheelSmokeTest {

    @Test
    void countryOnGeneratedMapGetsDeposits() {
        ContentPack pack = TestResourceMaps.PACK;
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
        List<Integer> country = continents.continents().getFirst().cells().stream()
                .limit(size.provincesPerCountry())
                .toList();

        StartResources resources = ResourceWheel.generate(rng.fork("resources"), pack, suitability, country);

        assertThat(resources.deposits()).isNotEmpty();
        assertThat(resources.deposits()).allSatisfy(deposit -> {
            assertThat(country).contains(deposit.cell());
            assertThat(suitability.suitability(deposit.cell(), deposit.resource()))
                    .isPositive();
        });
    }
}
