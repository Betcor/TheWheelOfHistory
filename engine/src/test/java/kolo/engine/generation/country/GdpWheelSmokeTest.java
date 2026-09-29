package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: лад і розвиненість ведуть ВВП, а його мітки йдуть у передісторію. */
class GdpWheelSmokeTest {

    @Test
    void regimeAndDevelopmentDriveGdpWhichFeedsBackstory() {
        Rng rng = Rng.of(1970);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), TestGdp.PACK);
        StartDevelopment development =
                DevelopmentWheel.generate(rng.fork("development"), TestGdp.PACK, regime.modifiers());
        StartGdp gdp = GdpWheel.generate(rng.fork("gdp"), TestGdp.PACK, regime.modifiers(), development);
        TreeSet<String> tags = new TreeSet<>(regime.tags());
        tags.addAll(development.tags());
        tags.addAll(gdp.tags());
        Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), TestGdp.PACK, tags, List.of());

        assertThat(gdp.rolls()).hasSize(1);
        assertThat(gdp.perCapita()).isPositive();
        assertThat(backstory.entries()).isNotEmpty();
        assertThat(backstory.tags()).containsAll(gdp.tags());
    }
}
