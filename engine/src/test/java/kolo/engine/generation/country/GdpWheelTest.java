package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestGdp.IDS;
import static kolo.engine.generation.country.TestGdp.PACK;
import static kolo.engine.generation.country.TestGdp.PER_CAPITA;
import static kolo.engine.generation.country.TestGdp.QUALITIES;
import static kolo.engine.generation.country.TestGdp.REGIME;
import static kolo.engine.generation.country.TestGdp.TIERS;
import static kolo.engine.generation.country.TestGdp.WEIGHTS;
import static kolo.engine.generation.country.TestGdp.development;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.GdpLevelId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GdpWheelTest {

    private static final int SEEDS = 2_000;
    private static final int STRENGTH = 50;

    @Test
    void sectorsAreLevelsInContentOrderWithContentWeightsTiersQualitiesAndTags() {
        List<Sector<GdpLevelDef>> sectors = GdpWheel.sectors(PACK);

        assertThat(sectors).extracting(Sector::id).containsExactly(IDS);
        assertThat(sectors.stream().mapToInt(Sector::weightBp).toArray()).isEqualTo(WEIGHTS);
        assertThat(sectors.stream().mapToInt(Sector::quality).toArray()).isEqualTo(QUALITIES);
        assertThat(sectors).extracting(Sector::tier).containsExactly(TIERS);
        assertThat(sectors.getFirst().tags()).containsExactly("poor");
        assertThat(sectors.get(2).tags()).isEmpty();
        assertThat(sectors.getLast().tags()).containsExactly("rich");
    }

    @Test
    void advantageIsRegimeThenEconomyThenSociety() {
        Advantage advantage = GdpWheel.advantage(PACK, REGIME.modifiers(), development(2, -1));

        assertThat(advantage.value()).isEqualTo(20 + 10 + 20 - 10);
        assertThat(advantage.modifiers())
                .extracting(AppliedModifier::sourceId, AppliedModifier::descriptionKey, AppliedModifier::value)
                .containsExactly(
                        tuple("ideology:democracy:0", "ideology.democracy", 20),
                        tuple("sub_ideology:liberal_democracy:0", "sub_ideology.liberal_democracy", 10),
                        tuple("development:economy", "development.economy", 20),
                        tuple("development:society", "development.society", -10));
    }

    @Test
    void worldLevelBranchesAddNoContributionAndOtherBranchesAreIgnored() {
        StartDevelopment development = new StartDevelopment(
                Map.of(
                        TechBranch.ECONOMY, 0,
                        TechBranch.SOCIETY, 0,
                        TechBranch.MILITARY, 2,
                        TechBranch.ENERGY_SCIENCE, -3),
                new TreeSet<>(),
                50,
                List.of());

        assertThat(GdpWheel.advantage(PACK, List.of(), development)).isEqualTo(Advantage.NONE);
        assertThat(GdpWheel.advantage(PACK, List.of(), development(-3, -3)).value())
                .isEqualTo(-60);
    }

    @Test
    void advantageIsClampedButExplainsEveryContribution() {
        Advantage advantage = GdpWheel.advantage(PACK, List.of(TestGdp.modifier("event", 90)), development(2, 1));

        assertThat(advantage.value()).isEqualTo(Advantage.MAX);
        assertThat(advantage.modifiers()).extracting(AppliedModifier::value).containsExactly(90, 20, 10);
    }

    @Test
    void developmentContributionIsLevelTimesCoefficient() {
        StartDevelopment development = development(-2, 0);

        assertThat(development.advantage(TechBranch.ECONOMY, 7))
                .contains(new AppliedModifier("development:economy", "development.economy", -14));
        assertThat(development.advantage(TechBranch.SOCIETY, 7)).isEmpty();
        assertThat(development.advantage(TechBranch.ECONOMY, 0)).isEmpty();
    }

    @Test
    void resultMatchesRolledLevel() {
        boolean[] seen = new boolean[IDS.length];
        for (long seed = 0; seed < SEEDS; seed++) {
            StartGdp gdp = GdpWheel.generate(Rng.of(seed), PACK, REGIME.modifiers(), development(1, 0));
            GdpLevelDef level = PACK.gdpLevel(gdp.level()).orElseThrow();
            RollRecord roll = gdp.rolls().getFirst();

            assertThat(gdp.rolls()).hasSize(1);
            assertThat(roll.kind()).isEqualTo(GdpWheel.KIND);
            assertThat(roll.resultSectorId()).isEqualTo(gdp.level().value());
            assertThat(roll.advantage()).isEqualTo(40);
            assertThat(roll.turn()).isZero();
            assertThat(roll.season()).isNull();
            assertThat(gdp.perCapita()).isEqualTo(level.perCapita());
            assertThat(gdp.tier()).isEqualTo(level.tier());
            assertThat(gdp.quality()).isEqualTo(level.quality());
            assertThat(gdp.tags()).containsExactlyElementsOf(new TreeSet<>(level.tags()));
            seen[Arrays.asList(IDS).indexOf(gdp.level().value())] = true;
        }
        assertThat(seen).containsOnly(true);
        assertThat(PER_CAPITA).isSorted();
    }

    @Test
    void usesItsOwnStream() {
        int direct = Rng.of(42).fork("per_capita").nextInt(Wheel.TOTAL_BP);
        StartGdp gdp = GdpWheel.generate(Rng.of(42), PACK, List.of(), development(0, 0));

        assertThat(gdp.rolls().getFirst().roll()).isEqualTo(direct);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageShiftsTowardsRicherLevelsAndKeepsExtremesPossible(int advantage) {
        int[] weights = Wheel.applyAdvantage(GdpWheel.sectors(PACK), advantage, STRENGTH).stream()
                .mapToInt(Sector::weightBp)
                .toArray();

        assertThat(Arrays.stream(weights).sum()).isEqualTo(Wheel.TOTAL_BP);
        assertThat(weights[0]).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(weights[4]).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        if (advantage > 0) {
            assertThat(weights[0]).isLessThan(WEIGHTS[0]);
            assertThat(weights[1]).isLessThan(WEIGHTS[1]);
            assertThat(weights[3]).isGreaterThan(WEIGHTS[3]);
            assertThat(weights[4]).isGreaterThan(WEIGHTS[4]);
        } else if (advantage < 0) {
            assertThat(weights[0]).isGreaterThan(WEIGHTS[0]);
            assertThat(weights[4]).isLessThan(WEIGHTS[4]);
        } else {
            assertThat(weights).isEqualTo(WEIGHTS);
        }
    }

    @Test
    void startGdpValidatesFields() {
        GdpLevelId middle = new GdpLevelId("middle");
        assertThat(new StartGdp(middle, 1000, OutcomeTier.PARTIAL, new TreeSet<>(List.of("b", "a")), 50, List.of())
                        .tags())
                .containsExactly("a", "b");
        assertOutOfRange(() -> new StartGdp(middle, 0, OutcomeTier.PARTIAL, new TreeSet<>(), 50, List.of()));
        assertOutOfRange(() -> new StartGdp(middle, 1000, OutcomeTier.PARTIAL, new TreeSet<>(), 101, List.of()));
        assertThatThrownBy(() -> new StartGdp(null, 1000, OutcomeTier.PARTIAL, new TreeSet<>(), 50, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StartGdp(middle, 1000, null, new TreeSet<>(), 50, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    private static void assertOutOfRange(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }
}
