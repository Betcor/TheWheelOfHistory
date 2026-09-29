package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.rng.Rng;
import kolo.engine.state.Training;
import org.junit.jupiter.api.Test;

/** Основний сценарій: лад → розвиненість → ВВП → розмір і вишкіл армії, а мітки армії ідуть у передісторію. */
class ArmyTrainingWheelSmokeTest {

    @Test
    void regimeGdpAndDevelopmentDriveTrainingWhichFeedsBackstory() {
        Rng rng = Rng.of(1970);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), TestTraining.PACK);
        StartDevelopment development =
                DevelopmentWheel.generate(rng.fork("development"), TestTraining.PACK, regime.modifiers());
        StartGdp gdp = GdpWheel.generate(rng.fork("gdp"), TestTraining.PACK, regime.modifiers(), development);
        StartArmySize army = ArmySizeWheel.generate(rng.fork("army_size"), TestTraining.PACK, regime.modifiers(), gdp);
        StartArmyTraining training = ArmyTrainingWheel.generate(
                rng.fork("army_training"), TestTraining.PACK, regime.modifiers(), gdp, development);
        TreeSet<String> tags = new TreeSet<>(regime.tags());
        tags.addAll(development.tags());
        tags.addAll(gdp.tags());
        tags.addAll(army.tags());
        tags.addAll(training.tags());
        Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), TestTraining.PACK, tags, List.of());

        assertThat(training.rolls()).hasSize(1);
        assertThat(training.level()).isBetween(Training.MIN, Training.MAX);
        assertThat(backstory.entries()).isNotEmpty();
        assertThat(backstory.tags()).containsAll(training.tags());
    }
}
