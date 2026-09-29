package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.ReliefDef;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.Relief;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReliefGeneratorTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final ReliefDef RELIEF = TestMaps.RELIEF;

    @Test
    void reliefCoversLandWithRidgesOnEveryContinent() {
        for (long seed = 0; seed < 20; seed++) {
            World world = world(seed, 3 + (int) (seed % 3), 400);

            ReliefMap relief = ReliefGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents());

            ReliefChecks.assertValid(world.grid(), world.continents(), relief, RELIEF);
            // Контент дає 1–2 хребти на материк, а материки тут — щонайменше по 20 провінцій.
            assertThat(relief.ridges())
                    .hasSizeGreaterThanOrEqualTo(world.continents().continents().size());
        }
    }

    @Test
    void ridgesRaiseMountainsAndPlainsStayFarFromThem() {
        World world = world(7, 1, 400);
        ReliefMap relief = ReliefGenerator.generate(Rng.of(7), PACK, world.grid(), world.continents());

        int[] fromRidge = ReliefGenerator.distances(world.grid(), world.continents(), relief.ridges());
        for (int cell : relief.heights().keySet()) {
            // Основа 20, хребет +60 зі спадом 20 за крок, шум ±20: гори (≥70) — не далі кроку від хребта.
            if (relief.reliefs().get(cell) == Relief.MOUNTAINS) {
                assertThat(fromRidge[cell]).isLessThanOrEqualTo(1);
            }
            if (fromRidge[cell] == 0) {
                assertThat(relief.reliefs().get(cell)).isIn(Relief.HILLS, Relief.MOUNTAINS);
            }
        }
        assertThat(relief.count(Relief.PLAIN)).isPositive();
        assertThat(relief.count(Relief.MOUNTAINS)).isPositive();
        assertThat(relief.count(Relief.PLAIN) + relief.count(Relief.HILLS) + relief.count(Relief.MOUNTAINS))
                .isEqualTo(400);
    }

    @Test
    void smallContinentGetsNoRidgesAndStaysLow() {
        // Хребет — лише на 1000 провінцій: жоден материк тут його не отримує; пагорби — з 41, а вище 40 шум не підніме.
        ContentPack pack = TestNames.pack(
                TestMaps.content(TestMaps.relief(new CountRange(1, 2), 1_000, 41, 70)), TestMaps.BALANCE);
        World world = world(3, 3, 300);

        ReliefMap relief = ReliefGenerator.generate(Rng.of(3), pack, world.grid(), world.continents());

        ReliefChecks.assertValid(
                world.grid(), world.continents(), relief, pack.map().relief());
        assertThat(relief.ridges()).isEmpty();
        assertThat(relief.rolls())
                .allSatisfy(roll -> assertThat(roll.resultSectorId()).isEqualTo("ridges_0"));
        assertThat(relief.count(Relief.PLAIN)).isEqualTo(300);
    }

    @Test
    void rollsAreNeutralRidgeWheelsInContinentOrder() {
        World world = world(9, 3, 300);

        ReliefMap relief = ReliefGenerator.generate(Rng.of(9), PACK, world.grid(), world.continents());

        assertThat(relief.rolls()).hasSize(3).allSatisfy(roll -> {
            assertThat(roll.kind()).isEqualTo(ReliefGenerator.RIDGES_KIND);
            assertThat(roll.advantage()).isZero();
            assertThat(roll.modifiers()).isEmpty();
            assertThat(roll.turn()).isZero();
            assertThat(roll.sectors()).extracting(RolledSector::id).containsExactly("ridges_1", "ridges_2");
            assertThat(roll.sectors())
                    .allSatisfy(sector -> assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL));
        });
    }

    @Test
    void sameSeedGivesSameRelief() {
        World world = world(21, 4, 350);

        assertThat(ReliefGenerator.generate(Rng.of(21), PACK, world.grid(), world.continents()))
                .isEqualTo(ReliefGenerator.generate(Rng.of(21), PACK, world.grid(), world.continents()));
    }

    @Test
    void otherSeedGivesOtherRelief() {
        World world = world(21, 4, 350);

        assertThat(ReliefGenerator.generate(Rng.of(1), PACK, world.grid(), world.continents()))
                .isNotEqualTo(ReliefGenerator.generate(Rng.of(2), PACK, world.grid(), world.continents()));
    }

    @Test
    void ridgeRangeIsCappedByContinentSize() {
        // Контент: 1–2 хребти, щонайменше 20 провінцій на хребет.
        assertThat(ReliefGenerator.ridgeRange(RELIEF, 100)).isEqualTo(new CountRange(1, 2));
        assertThat(ReliefGenerator.ridgeRange(RELIEF, 40)).isEqualTo(new CountRange(1, 2));
        assertThat(ReliefGenerator.ridgeRange(RELIEF, 39)).isEqualTo(new CountRange(1, 1));
        assertThat(ReliefGenerator.ridgeRange(RELIEF, 19)).isEqualTo(new CountRange(0, 0));
    }

    @Test
    void ridgeLengthIsShareOfContinentWidth() {
        // Довжина — 100% кореня з кількості провінцій, щонайменше одна комірка.
        assertThat(ReliefGenerator.ridgeLength(RELIEF, 400)).isEqualTo(20);
        assertThat(ReliefGenerator.ridgeLength(RELIEF, 399)).isEqualTo(19);
        assertThat(ReliefGenerator.ridgeLength(RELIEF, 1)).isEqualTo(1);
    }

    @Test
    void heightAddsRidgeFalloffAndNoiseWithinLimits() {
        // Основа 20, хребет +60, спад 20 за крок, шум ±20.
        assertThat(ReliefGenerator.height(RELIEF, 0, 0)).isEqualTo(80);
        assertThat(ReliefGenerator.height(RELIEF, 1, 0)).isEqualTo(60);
        assertThat(ReliefGenerator.height(RELIEF, 3, 0)).isEqualTo(20);
        assertThat(ReliefGenerator.height(RELIEF, 10, 0)).isEqualTo(20);
        assertThat(ReliefGenerator.height(RELIEF, Integer.MAX_VALUE, 0)).isEqualTo(20);
        assertThat(ReliefGenerator.height(RELIEF, 0, ValueNoise.MAX)).isEqualTo(100);
        assertThat(ReliefGenerator.height(RELIEF, 0, -ValueNoise.MAX)).isEqualTo(60);
        assertThat(ReliefGenerator.height(RELIEF, 1, ValueNoise.MAX / 2)).isEqualTo(70);

        ReliefDef steep = new ReliefDef(new CountRange(1, 1), 1, 100, 0, 100, 10, 10, 30, 1, RELIEF.levels());
        assertThat(ReliefGenerator.height(steep, 0, ValueNoise.MAX)).isEqualTo(ReliefDef.MAX_HEIGHT);
        assertThat(ReliefGenerator.height(steep, 50, -ValueNoise.MAX)).isZero();
    }

    @Test
    void distancesCountStepsOverLandFromRidges() {
        World world = world(5, 1, 200);
        List<Integer> land = world.continents().continents().getFirst().cells();
        Ridge ridge = new Ridge(0, List.of(land.getFirst()));

        int[] distance = ReliefGenerator.distances(world.grid(), world.continents(), List.of(ridge));

        assertThat(distance[land.getFirst()]).isZero();
        for (int cell = 0; cell < distance.length; cell++) {
            if (!world.continents().isLand(cell)) {
                assertThat(distance[cell]).isEqualTo(Integer.MAX_VALUE);
                continue;
            }
            assertThat(distance[cell]).isLessThan(Integer.MAX_VALUE);
            for (int neighbor : world.grid().cells().get(cell).neighbors()) {
                if (world.continents().isLand(neighbor)) {
                    assertThat(Math.abs(distance[cell] - distance[neighbor])).isLessThanOrEqualTo(1);
                }
            }
        }
    }

    @Test
    void isqrtRoundsDown() {
        assertThat(ReliefGenerator.isqrt(0)).isZero();
        assertThat(ReliefGenerator.isqrt(15)).isEqualTo(3);
        assertThat(ReliefGenerator.isqrt(16)).isEqualTo(4);
        assertThat(ReliefGenerator.isqrt(17)).isEqualTo(4);
        assertThat(ReliefGenerator.isqrt(Integer.MAX_VALUE)).isEqualTo(46_340);
    }

    @Test
    void continentsOfOtherGridAreRejected() {
        World world = world(1, 1, 100);
        MapGrid other = VoronoiGrid.generate(
                Rng.of(1), TestMaps.GRID, world.grid().cells().size() + 1);

        assertThatThrownBy(() -> ReliefGenerator.generate(Rng.of(1), PACK, other, world.continents()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageDoesNotChangeRidgeWheel(int advantage) {
        // Сектори кількості хребтів — PARTIAL: навіть гранична перевага лишає рівні ваги.
        assertThat(Wheel.applyAdvantage(
                        WorldSizeWheel.rangeSectors("ridges_", new CountRange(0, 3)), advantage, Wheel.MAX_STRENGTH))
                .extracting(Sector::weightBp)
                .containsExactly(2500, 2500, 2500, 2500);
    }

    static World world(long seed, int continents, int provinces) {
        WorldSize size = ContinentGeneratorTest.size(
                continents == 1 ? TestMaps.PANGAEA : TestMaps.ARCHIPELAGO, continents, provinces);
        MapGrid grid =
                ContinentGeneratorTest.grid(seed, continents == 1 ? TestMaps.PANGAEA : TestMaps.ARCHIPELAGO, size);
        return new World(grid, ContinentGenerator.generate(Rng.of(seed).fork("continents"), PACK, size, grid));
    }

    record World(MapGrid grid, ContinentMap continents) {}
}
