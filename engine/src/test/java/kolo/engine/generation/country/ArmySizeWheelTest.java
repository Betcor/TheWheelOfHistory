package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestArmy.IDS;
import static kolo.engine.generation.country.TestArmy.PACK;
import static kolo.engine.generation.country.TestArmy.QUALITIES;
import static kolo.engine.generation.country.TestArmy.REGIME;
import static kolo.engine.generation.country.TestArmy.SHARES_BP;
import static kolo.engine.generation.country.TestArmy.TIERS;
import static kolo.engine.generation.country.TestArmy.WEIGHTS;
import static kolo.engine.generation.country.TestHdi.gdp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ArmySizeId;
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

class ArmySizeWheelTest {

    private static final int SEEDS = 2_000;
    private static final int STRENGTH = 50;

    @Test
    void sectorsAreSizesInContentOrderWithContentWeightsTiersQualitiesAndTags() {
        List<Sector<ArmySizeDef>> sectors = ArmySizeWheel.sectors(PACK);

        assertThat(sectors).extracting(Sector::id).containsExactly(IDS);
        assertThat(sectors.stream().mapToInt(Sector::weightBp).toArray()).isEqualTo(WEIGHTS);
        assertThat(sectors.stream().mapToInt(Sector::quality).toArray()).isEqualTo(QUALITIES);
        assertThat(sectors).extracting(Sector::tier).containsExactly(TIERS);
        assertThat(sectors.getFirst().tags()).containsExactly("small_army");
        assertThat(sectors.get(2).tags()).isEmpty();
        assertThat(sectors.getLast().tags()).containsExactly("large_army");
    }

    @Test
    void advantageIsRegimeThenGdp() {
        Advantage advantage = ArmySizeWheel.advantage(PACK, REGIME.modifiers(), gdp(OutcomeTier.CRIT_SUCCESS));

        assertThat(advantage.value()).isEqualTo(10 + 15 + 20);
        assertThat(advantage.modifiers())
                .extracting(AppliedModifier::sourceId, AppliedModifier::descriptionKey, AppliedModifier::value)
                .containsExactly(
                        tuple("ideology:totalitarianism:0", "ideology.totalitarianism", 10),
                        tuple("sub_ideology:militarism:0", "sub_ideology.militarism", 15),
                        tuple("gdp:very_rich", "gdp.very_rich", 20));
    }

    @ParameterizedTest
    @EnumSource(OutcomeTier.class)
    void gdpContributionIsTierStepTimesCoefficient(OutcomeTier tier) {
        Advantage advantage = ArmySizeWheel.advantage(PACK, List.of(), gdp(tier));

        assertThat(advantage.value()).isEqualTo(tier.step() * TestArmy.GDP_ADVANTAGE);
        // Частковий ВВП нічого не додає, тож і рядка пояснення немає.
        assertThat(advantage.modifiers()).hasSize(tier == OutcomeTier.PARTIAL ? 0 : 1);
    }

    @Test
    void advantageIsClampedButExplainsEveryContribution() {
        Advantage advantage =
                ArmySizeWheel.advantage(PACK, List.of(TestArmy.modifier("event", -95)), gdp(OutcomeTier.CRIT_FAIL));

        assertThat(advantage.value()).isEqualTo(Advantage.MIN);
        assertThat(advantage.modifiers()).extracting(AppliedModifier::value).containsExactly(-95, -20);
    }

    @Test
    void resultMatchesRolledSize() {
        boolean[] seen = new boolean[IDS.length];
        for (long seed = 0; seed < SEEDS; seed++) {
            StartArmySize army = ArmySizeWheel.generate(Rng.of(seed), PACK, REGIME.modifiers(), gdp(OutcomeTier.FAIL));
            ArmySizeDef size = PACK.armySize(army.size()).orElseThrow();
            RollRecord roll = army.rolls().getFirst();

            assertThat(army.rolls()).hasSize(1);
            assertThat(roll.kind()).isEqualTo(ArmySizeWheel.KIND);
            assertThat(roll.resultSectorId()).isEqualTo(army.size().value());
            assertThat(roll.advantage()).isEqualTo(25 - 10);
            assertThat(roll.turn()).isZero();
            assertThat(roll.season()).isNull();
            assertThat(army.shareBp()).isEqualTo(size.shareBp());
            assertThat(army.quality()).isEqualTo(size.quality());
            assertThat(army.tags()).containsExactlyElementsOf(new TreeSet<>(size.tags()));
            seen[Arrays.asList(IDS).indexOf(army.size().value())] = true;
        }
        assertThat(seen).containsOnly(true);
        assertThat(SHARES_BP).isSorted();
    }

    @Test
    void usesItsOwnStream() {
        int direct = Rng.of(42).fork("army_size").nextInt(Wheel.TOTAL_BP);
        StartArmySize army = ArmySizeWheel.generate(Rng.of(42), PACK, List.of(), gdp(OutcomeTier.PARTIAL));

        assertThat(army.rolls().getFirst().roll()).isEqualTo(direct);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageShiftsTowardsLargerArmiesAndKeepsExtremesPossible(int advantage) {
        int[] weights = Wheel.applyAdvantage(ArmySizeWheel.sectors(PACK), advantage, STRENGTH).stream()
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
    void startArmySizeValidatesFields() {
        ArmySizeId regular = new ArmySizeId("regular");
        assertThat(new StartArmySize(regular, 150, new TreeSet<>(List.of("b", "a")), 50, List.of()).tags())
                .containsExactly("a", "b");
        assertThat(new StartArmySize(regular, 1, new TreeSet<>(), 0, List.of()).shareBp())
                .isEqualTo(1);
        assertThat(new StartArmySize(regular, ArmySizeDef.MAX_SHARE_BP, new TreeSet<>(), 100, List.of()).shareBp())
                .isEqualTo(ArmySizeDef.MAX_SHARE_BP);
        assertOutOfRange(() -> new StartArmySize(regular, 0, new TreeSet<>(), 50, List.of()));
        assertOutOfRange(
                () -> new StartArmySize(regular, ArmySizeDef.MAX_SHARE_BP + 1, new TreeSet<>(), 50, List.of()));
        assertOutOfRange(() -> new StartArmySize(regular, 150, new TreeSet<>(), 101, List.of()));
        assertThatThrownBy(() -> new StartArmySize(null, 150, new TreeSet<>(), 50, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    private static void assertOutOfRange(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }
}
