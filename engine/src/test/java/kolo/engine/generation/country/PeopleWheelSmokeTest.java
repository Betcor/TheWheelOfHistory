package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestPeople.PACK;
import static kolo.engine.generation.country.TestPeople.STYLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: держава отримує відомих людей з іменами, рисами й віком. */
class PeopleWheelSmokeTest {

    @Test
    void generatesNotablePeople() {
        StartPeople people = PeopleWheel.generate(Rng.of(1970), PACK, Set.of("democratic"), STYLE, Set.of());

        assertThat(people.people()).isNotEmpty();
        assertThat(people.people()).allSatisfy(person -> {
            assertThat(person.name().fullName().nominative()).isNotBlank();
            assertThat(person.traits()).isNotEmpty();
            assertThat(person.age()).isPositive();
        });
    }
}
