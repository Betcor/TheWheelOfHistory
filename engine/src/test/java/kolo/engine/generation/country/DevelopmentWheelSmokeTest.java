package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.rng.Rng;
import kolo.engine.state.TechBranch;
import org.junit.jupiter.api.Test;

/** Основний сценарій: лад дає перевагу розвиненості, а мітки розвиненості йдуть у передісторію. */
class DevelopmentWheelSmokeTest {

    @Test
    void regimeDrivesDevelopmentWhichFeedsBackstory() {
        Rng rng = Rng.of(1970);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), TestDevelopment.PACK);
        StartDevelopment development =
                DevelopmentWheel.generate(rng.fork("development"), TestDevelopment.PACK, regime.modifiers());
        TreeSet<String> tags = new TreeSet<>(regime.tags());
        tags.addAll(development.tags());
        Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), TestDevelopment.PACK, tags, List.of());

        assertThat(development.levels()).containsOnlyKeys(TechBranch.values());
        assertThat(development.rolls()).hasSize(4);
        assertThat(backstory.entries()).isNotEmpty();
        assertThat(backstory.tags()).containsAll(development.tags());
    }
}
