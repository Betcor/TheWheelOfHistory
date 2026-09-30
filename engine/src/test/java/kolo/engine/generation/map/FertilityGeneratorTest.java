package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.TreeMap;
import kolo.engine.content.ContentPack;
import kolo.engine.content.FertilityDef;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.state.Terrain;
import org.junit.jupiter.api.Test;

class FertilityGeneratorTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Test
    void fertilityFollowsTheTableOnGeneratedWorlds() {
        for (long seed = 0; seed < 10; seed++) {
            RiverGeneratorTest.Full world = RiverGeneratorTest.full(seed, 1 + (int) (seed % 5), 400);
            RiverMap rivers = RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea());

            FertilityMap fertility = FertilityGenerator.generate(PACK, world.climate(), rivers);

            FertilityChecks.assertValid(world.climate(), rivers, fertility, TestMaps.FERTILITY);
            // Родючість різна: таблиця відрізняє пояси й місцевість.
            assertThat(fertility.fertilities().values().stream().distinct().count())
                    .isGreaterThan(5);
        }
    }

    @Test
    void riverRaisesFertilityOnlyOfItsProvince() {
        RiverGeneratorTest.Full world = RiverGeneratorTest.full(11, 2, 400);
        RiverMap rivers = RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea());
        assertThat(rivers.cellRivers()).isNotEmpty();

        FertilityMap dry = FertilityGenerator.generate(pack(TestMaps.fertility(0)), world.climate(), rivers);
        FertilityMap wet = FertilityGenerator.generate(pack(TestMaps.fertility(30)), world.climate(), rivers);

        for (int cell : dry.fertilities().keySet()) {
            int before = dry.fertility(cell).orElseThrow();
            int after = wet.fertility(cell).orElseThrow();
            if (rivers.hasRiver(cell)) {
                assertThat(after).isEqualTo(Math.min(FertilityDef.MAX_VALUE, before + 30));
            } else {
                // Сусіди річки бонусу не отримують (рішення автора: лише провінція з річкою).
                assertThat(after).isEqualTo(before);
            }
        }
    }

    @Test
    void terrainPenaltyLowersFertility() {
        RiverGeneratorTest.Full world = RiverGeneratorTest.full(5, 1, 400);
        RiverMap rivers = RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea());
        TreeMap<Terrain, Integer> barren = new TreeMap<>(TestMaps.FERTILITY.terrains());
        barren.replaceAll((terrain, value) -> -FertilityDef.MAX_VALUE);
        FertilityDef def = new FertilityDef(TestMaps.FERTILITY.climates(), barren, 20, 0);

        FertilityMap fertility = FertilityGenerator.generate(pack(def), world.climate(), rivers);

        // Основа ≤ 60 і волога ≤ 20 не перекривають −100: суходіл безплідний.
        assertThat(fertility.fertilities().values()).containsOnly(0);
    }

    @Test
    void waterHasNoFertility() {
        RiverGeneratorTest.Full world = RiverGeneratorTest.full(3, 2, 200);
        RiverMap rivers = RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea());

        FertilityMap fertility = FertilityGenerator.generate(PACK, world.climate(), rivers);

        for (int cell = 0; cell < world.grid().cells().size(); cell++) {
            assertThat(fertility.fertility(cell).isPresent())
                    .isEqualTo(!world.sea().isWater(cell));
        }
    }

    @Test
    void mismatchedMapsAreRejected() {
        RiverGeneratorTest.Full world = RiverGeneratorTest.full(1, 1, 100);
        RiverGeneratorTest.Full other = RiverGeneratorTest.full(2, 1, 200);
        RiverMap rivers = RiverGenerator.generate(PACK, other.grid(), other.relief(), other.climate(), other.sea());

        assertThatThrownBy(() -> FertilityGenerator.generate(PACK, world.climate(), rivers))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @Test
    void fertilityMapRejectsValuesOutsideLimits() {
        assertThat(new FertilityMap(new TreeMap<>(Map.of(0, 0, 1, FertilityDef.MAX_VALUE))).fertility(1))
                .hasValue(FertilityDef.MAX_VALUE);
        assertThat(new FertilityMap(new TreeMap<>()).fertility(0)).isEmpty();
        assertThatThrownBy(() -> new FertilityMap(new TreeMap<>(Map.of(0, -1))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new FertilityMap(new TreeMap<>(Map.of(0, FertilityDef.MAX_VALUE + 1))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new FertilityMap(new TreeMap<>(Map.of(-1, 50))))
                .isInstanceOf(ValidationException.class);
    }

    private static ContentPack pack(FertilityDef fertility) {
        return TestNames.pack(TestMaps.content(fertility), TestMaps.BALANCE);
    }
}
