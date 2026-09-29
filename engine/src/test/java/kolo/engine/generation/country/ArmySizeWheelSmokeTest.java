package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: лад → розвиненість → ВВП → розмір армії, а мітки армії ідуть у передісторію. */
class ArmySizeWheelSmokeTest {

    @Test
    void regimeAndGdpDriveArmySizeWhichFeedsBackstory() {
        Rng rng = Rng.of(1970);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), TestArmy.PACK);
        StartDevelopment development =
                DevelopmentWheel.generate(rng.fork("development"), TestArmy.PACK, regime.modifiers());
        StartGdp gdp = GdpWheel.generate(rng.fork("gdp"), TestArmy.PACK, regime.modifiers(), development);
        StartArmySize army = ArmySizeWheel.generate(rng.fork("army_size"), TestArmy.PACK, regime.modifiers(), gdp);
        TreeSet<String> tags = new TreeSet<>(regime.tags());
        tags.addAll(development.tags());
        tags.addAll(gdp.tags());
        tags.addAll(army.tags());
        Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), TestArmy.PACK, tags, List.of());

        assertThat(army.rolls()).hasSize(1);
        assertThat(army.shareBp()).isBetween(1, ArmySizeDef.MAX_SHARE_BP);
        assertThat(backstory.entries()).isNotEmpty();
        assertThat(backstory.tags()).containsAll(army.tags());
    }
}
