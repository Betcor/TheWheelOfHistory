package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestHdi.HDI;
import static kolo.engine.generation.country.TestHdi.IDS;
import static kolo.engine.generation.country.TestHdi.PACK;
import static kolo.engine.generation.country.TestHdi.QUALITIES;
import static kolo.engine.generation.country.TestHdi.REGIME;
import static kolo.engine.generation.country.TestHdi.TIERS;
import static kolo.engine.generation.country.TestHdi.WEIGHTS;
import static kolo.engine.generation.country.TestHdi.gdp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.HdiLevelDef;
import kolo.engine.content.HdiLevelId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class HdiWheelTest {

    private static final int SEEDS = 2_000;
    private static final int STRENGTH = 50;

    @Test
    void sectorsAreLevelsInContentOrderWithContentWeightsTiersQualitiesAndTags() {
        List<Sector<HdiLevelDef>> sectors = HdiWheel.sectors(PACK);

        assertThat(sectors).extracting(Sector::id).containsExactly(IDS);
        assertThat(sectors.stream().mapToInt(Sector::weightBp).toArray()).isEqualTo(WEIGHTS);
        assertThat(sectors.stream().mapToInt(Sector::quality).toArray()).isEqualTo(QUALITIES);
        assertThat(sectors).extracting(Sector::tier).containsExactly(TIERS);
        assertThat(sectors.getFirst().tags()).isEmpty();
        assertThat(sectors.getLast().tags()).containsExactly("educated");
    }

    @Test
    void advantageIsRegimeThenGdp() {
        Advantage advantage = HdiWheel.advantage(PACK, REGIME.modifiers(), gdp(OutcomeTier.CRIT_SUCCESS));

        assertThat(advantage.value()).isEqualTo(10 + 10 + 30);
        assertThat(advantage.modifiers())
                .extracting(AppliedModifier::sourceId, AppliedModifier::descriptionKey, AppliedModifier::value)
                .containsExactly(
                        tuple("ideology:socialism:0", "ideology.socialism", 10),
                        tuple("sub_ideology:market_socialism:0", "sub_ideology.market_socialism", 10),
                        tuple("gdp:very_rich", "gdp.very_rich", 30));
    }

    @ParameterizedTest
    @EnumSource(OutcomeTier.class)
    void gdpContributionIsTierStepTimesCoefficient(OutcomeTier tier) {
        Advantage advantage = HdiWheel.advantage(PACK, List.of(), gdp(tier));

        assertThat(advantage.value()).isEqualTo(tier.step() * TestHdi.GDP_ADVANTAGE);
        // Частковий ВВП нічого не додає, тож і рядка пояснення немає.
        assertThat(advantage.modifiers()).hasSize(tier == OutcomeTier.PARTIAL ? 0 : 1);
    }

    @Test
    void gdpAdvantageIsLevelStepTimesPerStep() {
        StartGdp poor = gdp(OutcomeTier.FAIL);

        assertThat(poor.advantage(7)).contains(new AppliedModifier("gdp:poor", "gdp.poor", -7));
        assertThat(gdp(OutcomeTier.CRIT_FAIL).advantage(7))
                .contains(new AppliedModifier("gdp:destitute", "gdp.destitute", -14));
        assertThat(gdp(OutcomeTier.PARTIAL).advantage(7)).isEmpty();
        assertThat(poor.advantage(0)).isEmpty();
    }

    @Test
    void advantageIsClampedButExplainsEveryContribution() {
        Advantage advantage =
                HdiWheel.advantage(PACK, List.of(TestHdi.modifier("event", 90)), gdp(OutcomeTier.SUCCESS));

        assertThat(advantage.value()).isEqualTo(Advantage.MAX);
        assertThat(advantage.modifiers()).extracting(AppliedModifier::value).containsExactly(90, 15);
    }

    @Test
    void resultMatchesRolledLevel() {
        boolean[] seen = new boolean[IDS.length];
        for (long seed = 0; seed < SEEDS; seed++) {
            StartHdi hdi = HdiWheel.generate(Rng.of(seed), PACK, REGIME.modifiers(), gdp(OutcomeTier.FAIL));
            HdiLevelDef level = PACK.hdiLevel(hdi.level()).orElseThrow();
            RollRecord roll = hdi.rolls().getFirst();

            assertThat(hdi.rolls()).hasSize(1);
            assertThat(roll.kind()).isEqualTo(HdiWheel.KIND);
            assertThat(roll.resultSectorId()).isEqualTo(hdi.level().value());
            assertThat(roll.advantage()).isEqualTo(20 - 15);
            assertThat(roll.turn()).isZero();
            assertThat(roll.season()).isNull();
            assertThat(hdi.hdi()).isEqualTo(level.hdi());
            assertThat(hdi.quality()).isEqualTo(level.quality());
            assertThat(hdi.tags()).containsExactlyElementsOf(new TreeSet<>(level.tags()));
            seen[Arrays.asList(IDS).indexOf(hdi.level().value())] = true;
        }
        assertThat(seen).containsOnly(true);
        assertThat(HDI).isSorted();
    }

    @Test
    void usesItsOwnStream() {
        int direct = Rng.of(42).fork("hdi").nextInt(Wheel.TOTAL_BP);
        StartHdi hdi = HdiWheel.generate(Rng.of(42), PACK, List.of(), gdp(OutcomeTier.PARTIAL));

        assertThat(hdi.rolls().getFirst().roll()).isEqualTo(direct);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageShiftsTowardsHigherLevelsAndKeepsExtremesPossible(int advantage) {
        int[] weights = Wheel.applyAdvantage(HdiWheel.sectors(PACK), advantage, STRENGTH).stream()
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
    void startHdiValidatesFields() {
        HdiLevelId middle = new HdiLevelId("middle");
        assertThat(new StartHdi(middle, 55, new TreeSet<>(List.of("b", "a")), 50, List.of()).tags())
                .containsExactly("a", "b");
        assertThat(new StartHdi(middle, 0, new TreeSet<>(), 0, List.of()).hdi()).isZero();
        assertThat(new StartHdi(middle, 100, new TreeSet<>(), 100, List.of()).hdi())
                .isEqualTo(100);
        assertOutOfRange(() -> new StartHdi(middle, -1, new TreeSet<>(), 50, List.of()));
        assertOutOfRange(() -> new StartHdi(middle, 101, new TreeSet<>(), 50, List.of()));
        assertOutOfRange(() -> new StartHdi(middle, 55, new TreeSet<>(), 101, List.of()));
        assertThatThrownBy(() -> new StartHdi(null, 55, new TreeSet<>(), 50, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    private static void assertOutOfRange(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }
}
