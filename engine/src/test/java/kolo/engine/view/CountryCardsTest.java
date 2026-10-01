package kolo.engine.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.content.ResourceId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.world.StartWorld;
import kolo.engine.generation.world.WorldGenerator;
import kolo.engine.generation.world.WorldStates;
import kolo.engine.rng.Rng;
import kolo.engine.state.Country;
import kolo.engine.state.CountryId;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TestWorldStates;
import kolo.engine.state.WorldState;
import kolo.engine.wheel.RollRecord;
import org.junit.jupiter.api.Test;

class CountryCardsTest {

    @Test
    void cardTakesCountryProvincesPeopleAndReligion() {
        WorldState state = TestWorldStates.state();
        Country country = state.countries().get(TestWorldStates.FIRST);

        CountryCard card = CountryCards.of(state, 0);

        assertThat(card.number()).isZero();
        assertThat(card.name()).isEqualTo(country.name());
        assertThat(card.capital()).isEqualTo(Math.toIntExact(TestWorldStates.FIRST_CAPITAL.number()));
        assertThat(card.provinces()).isEqualTo(1);
        assertThat(card.populationK()).isEqualTo(500);
        assertThat(card.ideology()).isEqualTo(country.ideology());
        assertThat(card.subIdeology()).isEqualTo(country.subIdeology());
        assertThat(card.religion()).contains(state.religions().get(TestWorldStates.FAITH));
        assertThat(card.worldReligions())
                .containsExactly(state.religions().get(TestWorldStates.FAITH).name());
        assertThat(card.development()).isEqualTo(country.startDevelopment());
        assertThat(card.gdpPerCapita()).isEqualTo(country.gdpPerCapita());
        assertThat(card.hdi()).isEqualTo(country.hdi());
        assertThat(card.armyShareBp()).isEqualTo(country.armyShareBp());
        assertThat(card.training()).isEqualTo(country.training());
        assertThat(card.nuclear()).isEqualTo(country.nuclear());
        assertThat(card.fateTokens()).isEqualTo(country.fateTokens());
        assertThat(card.deposits())
                .containsExactly(new CountryCard.Deposit(
                        Math.toIntExact(TestWorldStates.FIRST_CAPITAL.number()), new ResourceId("iron")));
        assertThat(card.people()).singleElement().satisfies(person -> {
            assertThat(person.name())
                    .isEqualTo(state.people().get(TestWorldStates.LEADER).name());
            assertThat(person.bornTurn()).isEqualTo(-40);
            assertThat(person.alive()).isTrue();
        });
        assertThat(card.origin()).isEqualTo(country.origin());
        assertThat(card.modifiers()).isEqualTo(country.modifiers());
        assertThat(card.tags()).isEqualTo(country.tags());
        assertThat(card.rolls()).isEqualTo(country.generationRolls());
    }

    @Test
    void secularCountryHasNoReligion() {
        assertThat(CountryCards.of(TestWorldStates.state(), 1).religion()).isEmpty();
    }

    @Test
    void unknownCountryIsRejected() {
        assertThatThrownBy(() -> CountryCards.of(TestWorldStates.state(), 7))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    @Test
    void generatedCountryCarriesAllGenerationRolls() {
        long seed = 42;
        StartWorld world = WorldGenerator.generate(Rng.of(seed), TestNames.PACK, WorldSizeInput.of(2, NpcShare.FEW));
        WorldState state = WorldStates.of(seed, TestNames.PACK, world);

        for (Country country : state.countries().values()) {
            CountryCard card =
                    CountryCards.of(state, Math.toIntExact(country.id().number()));
            assertThat(card.rolls()).isNotEmpty().isEqualTo(country.generationRolls());
            assertThat(card.people()).hasSameSizeAs(country.people());
            // Сектор religion_<n> колеса державної релігії — n-та релігія світу.
            for (RollRecord roll : card.rolls()) {
                if (roll.kind().id().equals("generation_state_religion")
                        && roll.resultSectorId().startsWith("religion_")) {
                    int n = Integer.parseInt(roll.resultSectorId().substring("religion_".length()));
                    assertThat(card.religion().orElseThrow().name())
                            .isEqualTo(card.worldReligions().get(n));
                }
            }
            assertThat(card.provinces()).isEqualTo((int) state.provinces().values().stream()
                    .filter(p -> p.owner().equals(java.util.Optional.of(CountryId.of(card.number()))))
                    .count());
        }
    }
}
