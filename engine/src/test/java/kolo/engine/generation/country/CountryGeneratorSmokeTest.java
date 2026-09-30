package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.PowerComponent;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: з одного seed виходить держава з ладом, назвою, передісторією й людьми. */
class CountryGeneratorSmokeTest {

    @Test
    void generatesCountry() {
        StartCountry country =
                CountryGenerator.generate(Rng.of(1970), TestChain.NEUTRAL, TestChain.input(TestChain.NEUTRAL));

        assertThat(country.name().name().fullName().nominative()).isNotBlank();
        assertThat(country.backstory().entries()).isNotEmpty();
        assertThat(country.people().people()).isNotEmpty();
        assertThat(country.tags()).containsAll(country.regime().tags());
        assertThat(country.rolls()).isNotEmpty();
        assertThat(country.power().steps()).hasSize(PowerComponent.values().length);
    }
}
