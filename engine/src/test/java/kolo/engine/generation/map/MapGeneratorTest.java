package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.MapTemplateId;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.wheel.RollRecord;
import org.junit.jupiter.api.Test;

class MapGeneratorTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Test
    void stagesUseTheirOwnStreams() {
        Rng rng = Rng.of(42);
        WorldSizeInput input = WorldSizeInput.of(2, NpcShare.NORMAL);

        WorldMap map = MapGenerator.generate(Rng.of(42), PACK, input);

        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), PACK, input);
        MapTemplateDef template = PACK.map().template(size.template()).orElseThrow();
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), PACK.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(rng.fork("continents"), PACK, size, grid);
        ReliefMap relief = ReliefGenerator.generate(rng.fork("relief"), PACK, grid, continents);
        ClimateMap climate = ClimateGenerator.generate(rng.fork("climate"), PACK, grid, continents, relief);
        SeaMap sea = SeaGenerator.generate(rng.fork("sea"), PACK, grid, continents);
        RiverMap rivers = RiverGenerator.generate(PACK, grid, relief, climate, sea);
        FertilityMap fertility = FertilityGenerator.generate(PACK, climate, rivers);
        assertThat(map)
                .isEqualTo(new WorldMap(
                        size,
                        grid,
                        continents,
                        relief,
                        climate,
                        sea,
                        rivers,
                        fertility,
                        ResourceSuitabilityGenerator.generate(PACK, climate, fertility),
                        PlacementGenerator.generate(rng.fork("placement"), PACK, size, grid, continents)));
    }

    @Test
    void worldRollsAreSizeContinentsRidgesAndClimate() {
        WorldMap map = MapGenerator.generate(Rng.of(3), PACK, WorldSizeInput.of(1, NpcShare.MANY));

        List<RollRecord> expected = new ArrayList<>(map.size().rolls());
        expected.addAll(map.continents().rolls());
        expected.addAll(map.relief().rolls());
        expected.add(map.climate().roll());
        assertThat(map.rolls()).containsExactlyElementsOf(expected);
    }

    @Test
    void neighborsShareALandBorder() {
        WorldMap map = MapGenerator.generate(Rng.of(5), PACK, WorldSizeInput.of(3, NpcShare.MANY));

        for (int country = 0; country < map.countries(); country++) {
            for (int neighbor : map.neighbors(country)) {
                int self = country;
                assertThat(neighbor).isNotEqualTo(self);
                assertThat(map.neighbors(neighbor)).contains(self);
                assertThat(map.country(self).cells())
                        .anySatisfy(
                                cell -> assertThat(map.grid().cells().get(cell).neighbors())
                                        .anyMatch(other -> map.placement().country(other) == neighbor));
            }
        }
    }

    @Test
    void countryOutsideThePlacementIsRejected() {
        WorldMap map = MapGenerator.generate(Rng.of(7), PACK, WorldSizeInput.of(1, NpcShare.FEW));

        assertThatThrownBy(() -> map.country(map.countries()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() -> map.neighbors(-1)).isInstanceOf(ValidationException.class);
    }

    @Test
    void hostChoicesReachTheWorldSize() {
        MapTemplateId pangaea = TestMaps.PANGAEA.id();
        WorldMap map = MapGenerator.generate(
                Rng.of(9), PACK, new WorldSizeInput(2, NpcShare.FEW, Optional.of(pangaea), OptionalInt.empty()));

        assertThat(map.size().template()).isEqualTo(pangaea);
        assertThat(map.continents().continents()).hasSize(1);
    }
}
