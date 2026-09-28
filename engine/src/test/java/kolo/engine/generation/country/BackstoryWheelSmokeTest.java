package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestBackstory.COUNTRY;
import static kolo.engine.generation.country.TestBackstory.NEIGHBOR;
import static kolo.engine.generation.country.TestBackstory.PACK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import org.junit.jupiter.api.Test;

/** Основний сценарій: передісторія генерується й читається як текст. */
class BackstoryWheelSmokeTest {

    @Test
    void generatesReadableBackstory() {
        Backstory backstory =
                BackstoryWheel.generate(Rng.of(1970), PACK, Set.of("democratic"), List.of(CountryId.of(2)));

        assertThat(backstory.entries()).isNotEmpty();
        assertThat(backstory.entries())
                .allSatisfy(entry -> assertThat(
                                entry.text(COUNTRY, backstory.neighbor().map(id -> NEIGHBOR)))
                        .isNotBlank());
    }
}
