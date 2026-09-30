package kolo.engine.generation.religion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.IntPredicate;
import kolo.engine.content.ContentPack;
import kolo.engine.content.HolyCenterDef;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/**
 * Розподіл колеса святого центру відповідає вагам провінцій: частка нічийної землі й річок — як за формулою. Провінцій
 * багато, тож перевіряються частки груп, а не кожної провінції. Seed-и фіксовані — тест детермінований.
 */
class HolyCenterWheelDistributionTest {

    private static final int RUNS = 20_000;
    private static final WorldMap MAP = TestHolyCenters.MAP;

    /** Держава 0 сповідує релігію 0, решта — світські. */
    private static final List<OptionalInt> ONE_FOLLOWER = followerZero();

    @Test
    void unclaimedShareFollowsWeights() {
        HolyCenterDef def = new HolyCenterDef(10, 100, 30, 20);
        assertThat(observedUnclaimed(TestHolyCenters.pack(def))).isCloseTo(expectedUnclaimed(def), within(0.015));
    }

    @Test
    void equalWeightsGiveUnclaimedLandItsAreaShare() {
        HolyCenterDef def = new HolyCenterDef(10, 0, 0, 100);
        assertThat(observedUnclaimed(TestHolyCenters.pack(def))).isCloseTo(expectedUnclaimed(def), within(0.015));
    }

    @Test
    void riversAttractHolyCenters() {
        HolyCenterDef def = new HolyCenterDef(1, 0, 99, 100);
        ContentPack pack = TestHolyCenters.pack(def);
        long withRiver = 0;
        for (long seed = 0; seed < RUNS; seed++) {
            int cell = HolyCenterWheel.generate(Rng.of(seed), pack, MAP, 1, ONE_FOLLOWER)
                    .cell(0);
            if (MAP.rivers().hasRiver(cell)) {
                withRiver++;
            }
        }
        double expected = share(def, cell -> MAP.rivers().hasRiver(cell));
        assertThat(expected).isBetween(0.05, 0.99);
        assertThat((double) withRiver / RUNS).isCloseTo(expected, within(0.015));
    }

    private static double observedUnclaimed(ContentPack pack) {
        long unclaimed = 0;
        for (long seed = 0; seed < RUNS; seed++) {
            int cell = HolyCenterWheel.generate(Rng.of(seed), pack, MAP, 1, ONE_FOLLOWER)
                    .cell(0);
            if (!MAP.placement().isClaimed(cell)) {
                unclaimed++;
            }
        }
        return (double) unclaimed / RUNS;
    }

    private static double expectedUnclaimed(HolyCenterDef def) {
        double expected = share(def, cell -> !MAP.placement().isClaimed(cell));
        assertThat(expected).isBetween(0.02, 0.98);
        return expected;
    }

    /** Очікувана частка кандидатів релігії 0, що задовольняють умову, за вагами {@link HolyCenterDef#weight}. */
    private static double share(HolyCenterDef def, IntPredicate group) {
        TreeMap<Boolean, Long> weights = new TreeMap<>();
        for (int cell : MAP.fertility().fertilities().keySet()) {
            int owner = MAP.placement().country(cell);
            if (owner != 0 && MAP.placement().isClaimed(cell)) {
                continue;
            }
            int weight = def.weight(
                    !MAP.placement().isClaimed(cell),
                    MAP.fertility().fertility(cell).orElseThrow(),
                    MAP.rivers().hasRiver(cell));
            weights.merge(group.test(cell), (long) weight, Long::sum);
        }
        long in = weights.getOrDefault(true, 0L);
        return (double) in / (in + weights.getOrDefault(false, 0L));
    }

    private static List<OptionalInt> followerZero() {
        List<OptionalInt> religions = new ArrayList<>(TestHolyCenters.secular());
        religions.set(0, OptionalInt.of(0));
        return List.copyOf(religions);
    }
}
