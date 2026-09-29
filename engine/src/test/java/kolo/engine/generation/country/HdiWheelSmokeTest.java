package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.rng.Rng;
import kolo.engine.state.Stat;
import org.junit.jupiter.api.Test;

/** Основний сценарій: лад → розвиненість → ВВП → ІЛР, а мітки ІЛР ідуть у передісторію. */
class HdiWheelSmokeTest {

    @Test
    void regimeAndGdpDriveHdiWhichFeedsBackstory() {
        Rng rng = Rng.of(1970);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), TestHdi.PACK);
        StartDevelopment development =
                DevelopmentWheel.generate(rng.fork("development"), TestHdi.PACK, regime.modifiers());
        StartGdp gdp = GdpWheel.generate(rng.fork("gdp"), TestHdi.PACK, regime.modifiers(), development);
        StartHdi hdi = HdiWheel.generate(rng.fork("hdi"), TestHdi.PACK, regime.modifiers(), gdp);
        TreeSet<String> tags = new TreeSet<>(regime.tags());
        tags.addAll(development.tags());
        tags.addAll(gdp.tags());
        tags.addAll(hdi.tags());
        Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), TestHdi.PACK, tags, List.of());

        assertThat(hdi.rolls()).hasSize(1);
        assertThat((long) hdi.hdi()).isBetween(Stat.HDI.min(), Stat.HDI.max());
        assertThat(backstory.entries()).isNotEmpty();
        assertThat(backstory.tags()).containsAll(hdi.tags());
    }
}
