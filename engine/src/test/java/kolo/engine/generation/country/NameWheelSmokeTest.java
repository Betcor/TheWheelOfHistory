package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: держава отримує назву в усіх відмінках і стиль для імен своїх людей. */
class NameWheelSmokeTest {

    @Test
    void generatesCountryName() {
        StartName name = NameWheel.generate(Rng.of(1970), TestNames.PACK, NameWheelTest.LIBERAL, Set.of());

        assertThat(name.name().fullName().nominative()).isNotBlank();
        assertThat(name.name().shortName().nominative()).isNotBlank();
        assertThat(TestNames.PACK.names().styles()).containsKey(name.style());
        assertThat(name.roll().kind()).isEqualTo(NameWheel.KIND);
    }
}
