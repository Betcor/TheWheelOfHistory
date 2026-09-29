package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import kolo.engine.content.ClimateDef;
import kolo.engine.content.ClimateMoistureDef;
import kolo.engine.content.ClimateTemperatureDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.content.WorldClimateDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.Climate;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClimateGeneratorTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final ClimateDef CLIMATE = TestMaps.CLIMATE;

    @Test
    void climateCoversLandAndFollowsContent() {
        for (long seed = 0; seed < 15; seed++) {
            World world = world(seed, 1 + (int) (seed % 4), 300 + (int) seed * 20);

            ClimateMap climate = generate(seed, PACK, world);

            ClimateChecks.assertValid(world.continents(), world.relief(), climate, CLIMATE);
        }
    }

    @Test
    void polesAreColdAndEquatorIsWarm() {
        ContentPack temperate = TestNames.pack(
                TestMaps.content(TestMaps.climate(List.of(TestMaps.world("temperate", 1, 0)))), TestMaps.BALANCE);
        for (long seed = 0; seed < 10; seed++) {
            World world = world(seed, 1, 500);
            ClimateMap climate = generate(seed, temperate, world);

            for (int cell : climate.climates().keySet()) {
                int latitude = ClimateGenerator.latitude(
                        world.grid().cells().get(cell).site().y(), world.grid().height());
                // Екватор 90, −0,9 за одиницю широти, шум ±10, гори — до −30: полярний (< 15) можливий лише далі 38,
                // тропічний (≥ 70) — не далі 33.
                if (climate.climates().get(cell) == Climate.POLAR) {
                    assertThat(latitude).isGreaterThan(38);
                }
                if (climate.climates().get(cell) == Climate.TROPICAL) {
                    assertThat(latitude).isLessThanOrEqualTo(33);
                }
                // Далі 95 навіть найтепліша провінція (≤ 14) — полярна.
                if (latitude > 95) {
                    assertThat(climate.climates().get(cell)).isEqualTo(Climate.POLAR);
                }
            }
            assertThat(climate.count(Climate.TROPICAL)).isPositive();
        }
    }

    @Test
    void warmerWorldWarmsEveryProvince() {
        World world = world(4, 3, 400);
        ContentPack cold = TestNames.pack(
                TestMaps.content(TestMaps.climate(List.of(TestMaps.world("cold", 1, -20)))), TestMaps.BALANCE);
        ContentPack warm = TestNames.pack(
                TestMaps.content(TestMaps.climate(List.of(TestMaps.world("warm", 1, 20)))), TestMaps.BALANCE);

        ClimateMap colder = generate(4, cold, world);
        ClimateMap warmer = generate(4, warm, world);

        assertThat(colder.world().value()).isEqualTo("cold");
        assertThat(warmer.world().value()).isEqualTo("warm");
        for (int cell : colder.temperatures().keySet()) {
            assertThat(warmer.temperatures().get(cell))
                    .isGreaterThanOrEqualTo(colder.temperatures().get(cell));
        }
        assertThat(warmer.moistures()).isEqualTo(colder.moistures());
        assertThat(warmer.count(Climate.POLAR)).isLessThan(colder.count(Climate.POLAR));
        assertThat(warmer.count(Climate.TROPICAL)).isGreaterThan(colder.count(Climate.TROPICAL));
    }

    @Test
    void worldWheelIsNeutralAndWeightedByContent() {
        World world = world(9, 2, 300);

        ClimateMap climate = generate(9, PACK, world);

        assertThat(climate.roll().kind()).isEqualTo(ClimateGenerator.WORLD_KIND);
        assertThat(climate.roll().advantage()).isZero();
        assertThat(climate.roll().modifiers()).isEmpty();
        assertThat(climate.roll().turn()).isZero();
        assertThat(climate.roll().sectors())
                .extracting(RolledSector::id, RolledSector::weightBp, RolledSector::tier)
                .containsExactly(
                        tuple("cold", 2500, OutcomeTier.PARTIAL),
                        tuple("temperate", 5000, OutcomeTier.PARTIAL),
                        tuple("warm", 2500, OutcomeTier.PARTIAL));
    }

    @Test
    void sameSeedGivesSameClimate() {
        World world = world(21, 4, 350);

        assertThat(generate(21, PACK, world)).isEqualTo(generate(21, PACK, world));
    }

    @Test
    void otherSeedGivesOtherClimate() {
        World world = world(21, 4, 350);

        assertThat(ClimateGenerator.generate(Rng.of(1), PACK, world.grid(), world.continents(), world.relief()))
                .isNotEqualTo(
                        ClimateGenerator.generate(Rng.of(2), PACK, world.grid(), world.continents(), world.relief()));
    }

    @Test
    void latitudeGrowsFromEquatorToEdges() {
        assertThat(ClimateGenerator.latitude(50, 100)).isZero();
        assertThat(ClimateGenerator.latitude(25, 100)).isEqualTo(50);
        assertThat(ClimateGenerator.latitude(75, 100)).isEqualTo(50);
        assertThat(ClimateGenerator.latitude(0, 100)).isEqualTo(ClimateDef.MAX_VALUE);
        assertThat(ClimateGenerator.latitude(100, 100)).isEqualTo(ClimateDef.MAX_VALUE);
        assertThat(ClimateGenerator.latitude(101, 100)).isEqualTo(ClimateDef.MAX_VALUE);
    }

    @Test
    void temperatureFollowsLatitudeShiftHeightAndNoise() {
        // Екватор 90, полюс 0, охолодження 30% висоти, шум ±10.
        ClimateTemperatureDef def = CLIMATE.temperature();
        assertThat(ClimateGenerator.temperature(def, 0, 0, 0, 0)).isEqualTo(90);
        assertThat(ClimateGenerator.temperature(def, 50, 0, 0, 0)).isEqualTo(45);
        assertThat(ClimateGenerator.temperature(def, 100, 0, 0, 0)).isZero();
        assertThat(ClimateGenerator.temperature(def, 50, 10, 0, 0)).isEqualTo(55);
        assertThat(ClimateGenerator.temperature(def, 50, 0, 50, 0)).isEqualTo(30);
        assertThat(ClimateGenerator.temperature(def, 50, 0, 0, ValueNoise.MAX)).isEqualTo(55);
        assertThat(ClimateGenerator.temperature(def, 50, 0, 0, -ValueNoise.MAX)).isEqualTo(35);
        assertThat(ClimateGenerator.temperature(def, 100, -10, 100, -ValueNoise.MAX))
                .isZero();
        assertThat(ClimateGenerator.temperature(def, 0, 20, 0, ValueNoise.MAX)).isEqualTo(ClimateDef.MAX_VALUE);
    }

    @Test
    void moistureDriesInlandWithNoise() {
        // Берег 80, −10 за крок, шум ±20.
        ClimateMoistureDef def = CLIMATE.moisture();
        assertThat(ClimateGenerator.moisture(def, 1, 0)).isEqualTo(80);
        assertThat(ClimateGenerator.moisture(def, 3, 0)).isEqualTo(60);
        assertThat(ClimateGenerator.moisture(def, 20, 0)).isZero();
        assertThat(ClimateGenerator.moisture(def, Integer.MAX_VALUE, 0)).isZero();
        assertThat(ClimateGenerator.moisture(def, 1, ValueNoise.MAX)).isEqualTo(ClimateDef.MAX_VALUE);
        assertThat(ClimateGenerator.moisture(def, 2, -ValueNoise.MAX / 2)).isEqualTo(60);
    }

    @Test
    void fromSeaCountsStepsOverLand() {
        World world = world(5, 2, 250);

        int[] distance = ClimateGenerator.fromSea(world.grid(), world.continents());

        for (int cell = 0; cell < distance.length; cell++) {
            List<Integer> neighbors = world.grid().cells().get(cell).neighbors();
            if (!world.continents().isLand(cell)) {
                assertThat(distance[cell]).isZero();
                continue;
            }
            boolean coastal =
                    neighbors.stream().anyMatch(neighbor -> !world.continents().isLand(neighbor));
            assertThat(distance[cell] == 1).isEqualTo(coastal);
            for (int neighbor : neighbors) {
                assertThat(Math.abs(distance[cell] - distance[neighbor])).isLessThanOrEqualTo(1);
            }
        }
    }

    @Test
    void reliefOfOtherContinentsIsRejected() {
        World world = world(1, 1, 100);
        World other = world(1, 1, 120);

        assertThatThrownBy(() ->
                        ClimateGenerator.generate(Rng.of(1), PACK, world.grid(), world.continents(), other.relief()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() ->
                        ClimateGenerator.generate(Rng.of(1), PACK, other.grid(), world.continents(), world.relief()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageDoesNotChangeWorldWheel(int advantage) {
        // Сектори клімату світу — PARTIAL: навіть гранична перевага лишає ваги контенту.
        List<Sector<WorldClimateDef>> sectors = ClimateGenerator.worldSectors(CLIMATE.worlds());
        assertThat(Wheel.applyAdvantage(sectors, advantage, Wheel.MAX_STRENGTH))
                .extracting(Sector::weightBp)
                .containsExactly(2500, 5000, 2500);
    }

    static ClimateMap generate(long seed, ContentPack pack, World world) {
        return ClimateGenerator.generate(
                Rng.of(seed).fork("climate"), pack, world.grid(), world.continents(), world.relief());
    }

    static World world(long seed, int continents, int provinces) {
        ReliefGeneratorTest.World land = ReliefGeneratorTest.world(seed, continents, provinces);
        ReliefMap relief = ReliefGenerator.generate(Rng.of(seed).fork("relief"), PACK, land.grid(), land.continents());
        return new World(land.grid(), land.continents(), relief);
    }

    record World(MapGrid grid, ContinentMap continents, ReliefMap relief) {}
}
