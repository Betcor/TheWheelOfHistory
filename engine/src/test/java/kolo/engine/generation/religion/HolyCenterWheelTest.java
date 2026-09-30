package kolo.engine.generation.religion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.HolyCenterDef;
import kolo.engine.content.TestReligions;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.PlacementMap;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HolyCenterWheelTest {

    private static final WorldMap MAP = TestHolyCenters.MAP;
    private static final int NONE = PlacementMap.NONE;

    @Test
    void candidatesAreFollowerAndUnclaimedProvinces() {
        // Комірки 0..5: держава 0, держава 1, нічийна, держава 2, море (немає в суходолі), держава 1.
        List<Integer> owners = List.of(0, 1, NONE, 2, NONE, 1);
        TreeSet<Integer> land = new TreeSet<>(Set.of(0, 1, 2, 3, 5));

        assertThat(HolyCenterWheel.candidates(owners, land, new TreeSet<>(Set.of(1))))
                .containsExactly(1, 2, 5);
        assertThat(HolyCenterWheel.candidates(owners, land, new TreeSet<>(Set.of(0, 2))))
                .containsExactly(0, 2, 3);
    }

    @Test
    void religionWithoutFollowersStandsOnUnclaimedLand() {
        List<Integer> owners = List.of(0, 1, NONE, 2, NONE, 1);
        TreeSet<Integer> land = new TreeSet<>(Set.of(0, 1, 2, 3, 5));

        assertThat(HolyCenterWheel.candidates(owners, land, new TreeSet<>())).containsExactly(2);
    }

    @Test
    void withoutFollowersAndUnclaimedLandAnyProvinceIsCandidate() {
        List<Integer> owners = List.of(0, 1, NONE, 2);
        TreeSet<Integer> land = new TreeSet<>(Set.of(0, 1, 3));

        assertThat(HolyCenterWheel.candidates(owners, land, new TreeSet<>())).containsExactly(0, 1, 3);
    }

    @Test
    void sectorsFollowProvinceWeights() {
        HolyCenterDef def = TestReligions.HOLY_CENTER;
        List<Integer> candidates = HolyCenterWheel.candidates(
                MAP.placement().cellCountries(),
                new TreeSet<>(MAP.fertility().fertilities().keySet()),
                new TreeSet<>(Set.of(0)));

        List<Sector<Integer>> sectors = HolyCenterWheel.sectors(def, MAP, candidates);

        assertThat(sectors).extracting(Sector::value).containsExactlyElementsOf(candidates);
        assertThat(sectors).allSatisfy(sector -> {
            assertThat(sector.id()).isEqualTo("province_" + sector.value());
            assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL);
            assertThat(sector.quality()).isEqualTo(HolyCenterWheel.QUALITY);
            assertThat(sector.weightBp()).isPositive();
        });
        long total = 0;
        long[] weights = new long[candidates.size()];
        for (int i = 0; i < weights.length; i++) {
            int cell = candidates.get(i);
            weights[i] = def.weight(
                    !MAP.placement().isClaimed(cell),
                    MAP.fertility().fertility(cell).orElseThrow(),
                    MAP.rivers().hasRiver(cell));
            total += weights[i];
        }
        for (int i = 0; i < weights.length; i++) {
            assertThat(sectors.get(i).weightBp()).isEqualTo((int) Math.max(1, weights[i] * Wheel.TOTAL_BP / total));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-100, 0, 100})
    void advantageDoesNotChangeWeights(int advantage) {
        List<Integer> candidates = HolyCenterWheel.candidates(
                MAP.placement().cellCountries(),
                new TreeSet<>(MAP.fertility().fertilities().keySet()),
                new TreeSet<>(Set.of(0, 1)));
        List<Sector<Integer>> sectors = HolyCenterWheel.sectors(TestReligions.HOLY_CENTER, MAP, candidates);
        List<Sector<Integer>> base = Wheel.applyAdvantage(sectors, 0, Wheel.MAX_STRENGTH);

        assertThat(Wheel.applyAdvantage(sectors, advantage, Wheel.MAX_STRENGTH)).isEqualTo(base);
    }

    @Test
    void everyReligionGetsCenterAmongItsCandidates() {
        List<OptionalInt> religions = TestHolyCenters.roundRobin(2);
        for (long seed = 0; seed < 100; seed++) {
            StartHolyCenters centers = HolyCenterWheel.generate(Rng.of(seed), TestHolyCenters.PACK, MAP, 3, religions);

            assertThat(centers.centers()).hasSize(3);
            for (int r = 0; r < 3; r++) {
                int owner = MAP.placement().country(centers.cell(r));
                int religion = r;
                assertThat(owner == NONE || religions.get(owner).equals(OptionalInt.of(religion)))
                        .isTrue();
            }
            // Релігія 2 не має держав — лише нічийна земля.
            assertThat(MAP.placement().isClaimed(centers.cell(2))).isFalse();
        }
    }

    @Test
    void rollRecordsTheProvinceAndNoAdvantage() {
        StartHolyCenters centers =
                HolyCenterWheel.generate(Rng.of(5), TestHolyCenters.PACK, MAP, 2, TestHolyCenters.roundRobin(2));

        assertThat(centers.rolls()).hasSize(2);
        for (int r = 0; r < 2; r++) {
            RollRecord roll = centers.rolls().get(r);
            assertThat(roll.kind()).isEqualTo(HolyCenterWheel.KIND);
            assertThat(roll.advantage()).isZero();
            assertThat(roll.modifiers()).isEmpty();
            assertThat(roll.turn()).isZero();
            assertThat(roll.resultSectorId()).isEqualTo("province_" + centers.cell(r));
        }
    }

    @Test
    void religionsDoNotShiftEachOther() {
        List<OptionalInt> religions = TestHolyCenters.roundRobin(2);
        StartHolyCenters two = HolyCenterWheel.generate(Rng.of(9), TestHolyCenters.PACK, MAP, 2, religions);
        StartHolyCenters three = HolyCenterWheel.generate(Rng.of(9), TestHolyCenters.PACK, MAP, 3, religions);

        assertThat(three.centers().subList(0, 2)).isEqualTo(two.centers());
    }

    @Test
    void oneProvinceMayBeHolyForSeveralReligions() {
        // Жодна релігія не має держав: кандидати — ті самі нічийні провінції, і центри вір не виключають одне одного.
        TreeSet<Integer> unclaimed = new TreeSet<>();
        MAP.fertility().fertilities().keySet().stream()
                .filter(cell -> !MAP.placement().isClaimed(cell))
                .forEach(unclaimed::add);
        assertThat(unclaimed).isNotEmpty();
        int shared = 0;
        for (long seed = 0; seed < 50; seed++) {
            StartHolyCenters centers =
                    HolyCenterWheel.generate(Rng.of(seed), TestHolyCenters.PACK, MAP, 12, TestHolyCenters.secular());
            assertThat(centers.centers())
                    .allSatisfy(center -> assertThat(unclaimed).contains(center.cell()));
            TreeSet<Integer> cells = new TreeSet<>();
            centers.centers().forEach(center -> cells.add(center.cell()));
            shared += 12 - cells.size();
        }
        assertThat(shared).isPositive();
    }

    @Test
    void noReligionsGiveNoCenters() {
        assertThat(HolyCenterWheel.generate(Rng.of(1), TestHolyCenters.PACK, MAP, 0, TestHolyCenters.secular())
                        .centers())
                .isEmpty();
    }

    @Test
    void rejectsInconsistentInput() {
        List<OptionalInt> tooFew = TestHolyCenters.secular().subList(1, MAP.countries());
        assertThatThrownBy(() -> HolyCenterWheel.generate(Rng.of(1), TestHolyCenters.PACK, MAP, 2, tooFew))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        List<OptionalInt> unknown = TestHolyCenters.roundRobin(3);
        assertThatThrownBy(() -> HolyCenterWheel.generate(Rng.of(1), TestHolyCenters.PACK, MAP, 2, unknown))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() -> HolyCenterWheel.generate(Rng.of(1), TestHolyCenters.PACK, MAP, -1, tooFew))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resultValidatesCell() {
        RollRecord roll = HolyCenterWheel.generate(Rng.of(5), TestHolyCenters.PACK, MAP, 1, TestHolyCenters.secular())
                .rolls()
                .getFirst();
        assertThatThrownBy(() -> new HolyCenter(-1, roll)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new HolyCenter(0, null)).isInstanceOf(NullPointerException.class);
    }
}
