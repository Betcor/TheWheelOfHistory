package kolo.engine.state;

import static kolo.engine.state.TestWorldStates.FAITH;
import static kolo.engine.state.TestWorldStates.FIRST;
import static kolo.engine.state.TestWorldStates.LEADER;
import static kolo.engine.state.TestWorldStates.SECOND;
import static kolo.engine.state.TestWorldStates.SECOND_CAPITAL;
import static kolo.engine.state.TestWorldStates.UNCLAIMED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Consumer;
import kolo.engine.error.InvariantViolationException;
import org.junit.jupiter.api.Test;

class WorldInvariantsTest {

    @Test
    void validStatePasses() {
        assertThatCode(() -> WorldInvariants.check(TestWorldStates.state())).doesNotThrowAnyException();
    }

    @Test
    void provincesAreExactlyTheLand() {
        assertViolation("provinces_are_land", state -> state.provinces().remove(UNCLAIMED));
        assertViolation(
                "provinces_are_land",
                state -> state.provinces().put(ProvinceId.of(3), TestWorldStates.province(ProvinceId.of(3), null, 0)));
    }

    @Test
    void provincesReferToExistingCountries() {
        assertViolation(
                "province_owner", state -> state.provinces().get(UNCLAIMED).setOwner(CountryId.of(9)));
        assertViolation(
                "province_controller", state -> state.provinces().get(UNCLAIMED).setController(CountryId.of(9)));
    }

    @Test
    void capitalIsOwnedByTheCountry() {
        assertViolation("capital_owned", state -> state.countries().get(FIRST).setCapital(SECOND_CAPITAL));
        assertViolation(
                "capital_owned", state -> state.provinces().get(SECOND_CAPITAL).setOwner(FIRST));
    }

    @Test
    void countryValuesStayInBounds() {
        assertViolation(
                "country_religion", state -> state.countries().get(SECOND).setReligion(ReligionId.of(9)));
        assertViolation("country_hdi", state -> state.countries().get(FIRST).setHdi(101));
        assertViolation("army_share_bp", state -> state.countries().get(FIRST).setArmyShareBp(10_001));
        assertViolation("training", state -> state.countries().get(FIRST).setTraining(Training.MAX + 1));
        assertViolation("warheads", state -> state.countries().get(FIRST).setNuclear(NuclearStatus.ARSENAL, 0));
        assertViolation("warheads", state -> state.countries().get(FIRST).setNuclear(NuclearStatus.PROGRAM, 2));
        assertViolation("fate_tokens", state -> state.countries().get(FIRST).setFateTokens(FateTokens.MAX + 1));
        assertViolation("fate_tokens", state -> state.countries().get(FIRST).setFateTokens(-1));
    }

    @Test
    void peopleAndCountriesReferToEachOther() {
        assertViolation(
                "country_people",
                state -> state.countries().get(SECOND).people().add(LEADER));
        assertViolation("country_people", state -> state.people().remove(LEADER));
        assertViolation(
                "person_country", state -> state.countries().get(FIRST).people().clear());
        assertViolation("country_people_unique", state -> {
            List<PersonId> people = state.countries().get(FIRST).people();
            people.add(people.getFirst());
        });
    }

    @Test
    void holyCenterIsAProvince() {
        assertViolation(
                "holy_center",
                state -> state.religions().put(FAITH, TestWorldStates.religion(FAITH, ProvinceId.of(3))));
    }

    @Test
    void sharedCounterIsAheadOfEveryIssuedId() {
        WorldState behind = new WorldState(
                WorldState.SCHEMA_VERSION,
                "0".repeat(64),
                1,
                0,
                3,
                TestWorldStates.map(),
                TestWorldStates.state().countries(),
                TestWorldStates.state().provinces(),
                TestWorldStates.state().people(),
                TestWorldStates.state().religions(),
                List.of());
        assertViolation("next_id_seq", behind);

        // Держава й релігія з тим самим номером лічильника.
        assertViolation("id_seq_unique", state -> {
            state.religions().remove(FAITH);
            state.religions().put(ReligionId.of(1), TestWorldStates.religion(ReligionId.of(1), UNCLAIMED));
            state.countries().get(FIRST).setReligion(ReligionId.of(1));
        });
    }

    private static void assertViolation(String check, Consumer<WorldState> change) {
        WorldState state = TestWorldStates.state();
        change.accept(state);
        assertViolation(check, state);
    }

    private static void assertViolation(String check, WorldState state) {
        assertThatThrownBy(() -> WorldInvariants.check(state))
                .isInstanceOfSatisfying(
                        InvariantViolationException.class,
                        e -> assertThat(e.details()).containsEntry("check", check));
    }
}
