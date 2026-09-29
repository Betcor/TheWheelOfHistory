package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestNuclear.IRON;
import static kolo.engine.generation.country.TestNuclear.PACK;
import static kolo.engine.generation.country.TestNuclear.QUALITIES;
import static kolo.engine.generation.country.TestNuclear.REGIME;
import static kolo.engine.generation.country.TestNuclear.URANIUM;
import static kolo.engine.generation.country.TestNuclear.WARHEADS;
import static kolo.engine.generation.country.TestNuclear.WEIGHTS;
import static kolo.engine.generation.country.TestNuclear.development;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.ResourceId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.state.NuclearStatus;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NuclearWheelTest {

    private static final int SEEDS = 2_000;
    private static final int STRENGTH = 50;
    private static final List<ResourceId> WITH_URANIUM = List.of(IRON, URANIUM);

    @Test
    void sectorsAreStatusesInOrderWithContentWeightsQualitiesAndTags() {
        List<Sector<NuclearStatusDef>> sectors = NuclearWheel.sectors(PACK, true);

        assertThat(sectors).extracting(Sector::id).containsExactly("none", "program", "arsenal");
        assertThat(sectors.stream().mapToInt(Sector::weightBp).toArray()).isEqualTo(WEIGHTS);
        assertThat(sectors.stream().mapToInt(Sector::quality).toArray()).isEqualTo(QUALITIES);
        assertThat(sectors)
                .extracting(Sector::tier)
                .containsExactly(OutcomeTier.FAIL, OutcomeTier.SUCCESS, OutcomeTier.CRIT_SUCCESS);
        assertThat(sectors.getLast().tags()).containsExactly("nuclear_power");
    }

    @Test
    void withoutArsenalOnlyNoneAndProgramRemain() {
        assertThat(NuclearWheel.sectors(PACK, false)).extracting(Sector::id).containsExactly("none", "program");
    }

    @Test
    void arsenalNeedsNuclearFuelAndEnergyNotBelowMinusOne() {
        assertThat(NuclearWheel.arsenalAllowed(PACK, development(-1), WITH_URANIUM))
                .isTrue();
        assertThat(NuclearWheel.arsenalAllowed(PACK, development(2), List.of(URANIUM, URANIUM)))
                .isTrue();
        // GD §4.6: без урану — ні; з відставанням −2 і нижче — теж ні, навіть з ураном.
        assertThat(NuclearWheel.arsenalAllowed(PACK, development(-2), WITH_URANIUM))
                .isFalse();
        assertThat(NuclearWheel.arsenalAllowed(PACK, development(-3), WITH_URANIUM))
                .isFalse();
        assertThat(NuclearWheel.arsenalAllowed(PACK, development(2), List.of(IRON)))
                .isFalse();
        assertThat(NuclearWheel.arsenalAllowed(PACK, development(2), List.of())).isFalse();
    }

    @Test
    void unknownResourceIsRejected() {
        assertThatThrownBy(() -> NuclearWheel.generate(
                        Rng.of(1), PACK, List.of(), development(0), List.of(new ResourceId("unobtainium"))))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    @Test
    void withoutArsenalItIsNeverRolled() {
        for (long seed = 0; seed < SEEDS; seed++) {
            // Найбільша перевага: без заборони арсенал випадав би часто.
            StartNuclear nuclear =
                    NuclearWheel.generate(Rng.of(seed), PACK, List.of(modifier(100)), development(-2), WITH_URANIUM);

            assertThat(nuclear.status()).isNotEqualTo(NuclearStatus.ARSENAL);
            assertThat(nuclear.rolls().getFirst().sectors())
                    .extracting(RolledSector::id)
                    .containsExactly("none", "program");
        }
    }

    @Test
    void advantageIsRegimeThenEnergyContribution() {
        Advantage advantage = NuclearWheel.advantage(PACK, REGIME.modifiers(), development(2));

        assertThat(advantage.value()).isEqualTo(20 + 10 + 20);
        assertThat(advantage.modifiers())
                .extracting(AppliedModifier::sourceId, AppliedModifier::descriptionKey, AppliedModifier::value)
                .containsExactly(
                        tuple("ideology:totalitarianism:0", "ideology.totalitarianism", 20),
                        tuple("sub_ideology:militarism:0", "sub_ideology.militarism", 10),
                        tuple("development:energy_science", "development.energy_science", 20));
    }

    @Test
    void worldLevelEnergyAddsNoContributionAndLagSubtracts() {
        assertThat(NuclearWheel.advantage(PACK, List.of(), development(0))).isEqualTo(Advantage.NONE);
        assertThat(NuclearWheel.advantage(PACK, List.of(), development(-3)).value())
                .isEqualTo(-30);
    }

    @Test
    void advantageIsClampedButExplainsEveryContribution() {
        Advantage advantage = NuclearWheel.advantage(PACK, List.of(modifier(95)), development(2));

        assertThat(advantage.value()).isEqualTo(Advantage.MAX);
        assertThat(advantage.modifiers()).extracting(AppliedModifier::value).containsExactly(95, 20);
    }

    @Test
    void rollsRecordStatusAndForArsenalWarheads() {
        boolean arsenalSeen = false;
        boolean otherSeen = false;
        for (long seed = 0; seed < SEEDS; seed++) {
            StartNuclear nuclear =
                    NuclearWheel.generate(Rng.of(seed), PACK, REGIME.modifiers(), development(1), WITH_URANIUM);
            NuclearStatusDef status = PACK.nuclearStatus(nuclear.status());
            RollRecord statusRoll = nuclear.rolls().getFirst();

            assertThat(statusRoll.kind()).isEqualTo(NuclearWheel.KIND);
            assertThat(statusRoll.resultSectorId()).isEqualTo(nuclear.status().key());
            assertThat(statusRoll.advantage()).isEqualTo(40);
            assertThat(statusRoll.turn()).isZero();
            assertThat(statusRoll.season()).isNull();
            assertThat(nuclear.tags()).containsExactlyElementsOf(new TreeSet<>(status.tags()));
            assertThat(nuclear.quality()).isEqualTo(status.quality());
            if (nuclear.status() == NuclearStatus.ARSENAL) {
                arsenalSeen = true;
                assertThat(nuclear.rolls()).hasSize(2);
                RollRecord warheads = nuclear.rolls().getLast();
                assertThat(warheads.kind()).isEqualTo(NuclearWheel.WARHEADS_KIND);
                assertThat(warheads.advantage()).isZero();
                assertThat(warheads.resultSectorId()).isEqualTo("warheads_" + nuclear.warheads());
                assertThat(WARHEADS.contains(nuclear.warheads())).isTrue();
            } else {
                otherSeen = true;
                assertThat(nuclear.rolls()).hasSize(1);
                assertThat(nuclear.warheads()).isZero();
            }
        }
        assertThat(arsenalSeen).isTrue();
        assertThat(otherSeen).isTrue();
    }

    @Test
    void warheadSectorsAreEqualAndNeutral() {
        List<Sector<Integer>> sectors = NuclearWheel.warheadSectors(PACK);

        assertThat(sectors)
                .extracting(Sector::id)
                .containsExactly("warheads_2", "warheads_3", "warheads_4", "warheads_5");
        assertThat(sectors).extracting(Sector::value).containsExactly(2, 3, 4, 5);
        assertThat(sectors).allSatisfy(sector -> {
            assertThat(sector.weightBp()).isEqualTo(1);
            assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL);
            assertThat(sector.quality()).isEqualTo(NuclearWheel.WARHEADS_QUALITY);
        });
    }

    @Test
    void statusAndWarheadStreamsAreIndependent() {
        int direct = Rng.of(42).fork("status").nextInt(Wheel.TOTAL_BP);
        StartNuclear nuclear = NuclearWheel.generate(Rng.of(42), PACK, List.of(), development(0), WITH_URANIUM);

        assertThat(nuclear.rolls().getFirst().roll()).isEqualTo(direct);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageShiftsTowardsWeaponsAndKeepsArsenalPossible(int advantage) {
        int[] weights = Wheel.applyAdvantage(NuclearWheel.sectors(PACK, true), advantage, STRENGTH).stream()
                .mapToInt(Sector::weightBp)
                .toArray();

        assertThat(Arrays.stream(weights).sum()).isEqualTo(Wheel.TOTAL_BP);
        assertThat(weights[2]).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        if (advantage > 0) {
            assertThat(weights[0]).isLessThan(WEIGHTS[0]);
            assertThat(weights[2]).isGreaterThan(WEIGHTS[2]);
        } else if (advantage < 0) {
            assertThat(weights[0]).isGreaterThan(WEIGHTS[0]);
            assertThat(weights[2]).isLessThan(WEIGHTS[2]);
        } else {
            assertThat(weights).isEqualTo(WEIGHTS);
        }
    }

    @Test
    void startNuclearKeepsWarheadsConsistentWithStatus() {
        assertThat(new StartNuclear(NuclearStatus.ARSENAL, 3, new TreeSet<>(), 90, List.of()).warheads())
                .isEqualTo(3);
        assertOutOfRange(() -> new StartNuclear(NuclearStatus.ARSENAL, 0, new TreeSet<>(), 90, List.of()));
        assertOutOfRange(() -> new StartNuclear(NuclearStatus.PROGRAM, 1, new TreeSet<>(), 70, List.of()));
        assertOutOfRange(() -> new StartNuclear(NuclearStatus.NONE, 0, new TreeSet<>(), 101, List.of()));
    }

    private static void assertOutOfRange(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    private static Modifier modifier(int value) {
        return TestNuclear.modifier("test", value);
    }
}
