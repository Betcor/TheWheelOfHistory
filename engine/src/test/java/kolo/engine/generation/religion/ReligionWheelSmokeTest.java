package kolo.engine.generation.religion;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: світ отримує кілька релігій з назвами, аспектами, догматами й устроєм. */
class ReligionWheelSmokeTest {

    @Test
    void generatesWorldReligions() {
        StartReligions world = WorldReligionsWheel.generate(Rng.of(1970), TestNames.PACK, 12);

        assertThat(world.religions()).isNotEmpty();
        assertThat(world.religions()).allSatisfy(religion -> {
            assertThat(religion.name().nominative()).isNotBlank();
            assertThat(religion.aspects()).isNotEmpty();
            assertThat(religion.dogmas()).isNotEmpty();
            assertThat(religion.tags()).isNotEmpty();
        });
    }
}
