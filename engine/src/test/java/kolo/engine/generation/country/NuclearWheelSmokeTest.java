package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: лад і розвиненість ведуть ядерний статус, а його мітки йдуть у передісторію. */
class NuclearWheelSmokeTest {

    @Test
    void regimeAndDevelopmentDriveNuclearStatusWhichFeedsBackstory() {
        Rng rng = Rng.of(1970);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), TestNuclear.PACK);
        StartDevelopment development =
                DevelopmentWheel.generate(rng.fork("development"), TestNuclear.PACK, regime.modifiers());
        StartNuclear nuclear = NuclearWheel.generate(
                rng.fork("nuclear"), TestNuclear.PACK, regime.modifiers(), development, List.of(TestNuclear.URANIUM));
        TreeSet<String> tags = new TreeSet<>(regime.tags());
        tags.addAll(development.tags());
        tags.addAll(nuclear.tags());
        Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), TestNuclear.PACK, tags, List.of());

        assertThat(nuclear.rolls()).isNotEmpty();
        assertThat(backstory.entries()).isNotEmpty();
        assertThat(backstory.tags()).containsAll(nuclear.tags());
    }
}
