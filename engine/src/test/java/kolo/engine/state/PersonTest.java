package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.TraitId;
import org.junit.jupiter.api.Test;

class PersonTest {

    @Test
    void copyIsEqualButIndependent() {
        Person original = TestWorldStates.person(PersonId.of(3), CountryId.of(0));
        Person copy = original.copy();

        assertThat(copy).isEqualTo(original).isNotSameAs(original);
        copy.setAlive(false);
        copy.setCountry(CountryId.of(1));
        copy.traits().add(new TraitId("brave"));

        assertThat(original.alive()).isTrue();
        assertThat(original.country()).isEqualTo(CountryId.of(0));
        assertThat(original.traits()).containsExactly(new TraitId("loyal"));
    }

    @Test
    void ageCountsFromBirthTurn() {
        Person person = TestWorldStates.person(PersonId.of(3), CountryId.of(0));

        assertThat(person.age(0)).isEqualTo(40);
        assertThat(person.age(10)).isEqualTo(50);
    }
}
