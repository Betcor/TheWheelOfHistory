package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestDevelopment.PACK;
import static kolo.engine.generation.country.TestDevelopment.QUALITIES;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.SourceKind;
import kolo.engine.rng.Rng;
import kolo.engine.state.Development;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Властивості колеса розвиненості на довільних seed і перевагах. */
class DevelopmentWheelPropertiesTest {

    @Property
    void sameSeedGivesSameDevelopment(@ForAll long seed) {
        List<Modifier> modifiers = TestDevelopment.REGIME.modifiers();

        assertThat(DevelopmentWheel.generate(Rng.of(seed), PACK, modifiers))
                .isEqualTo(DevelopmentWheel.generate(Rng.of(seed), PACK, modifiers));
    }

    @Property
    void developmentIsConsistentWithItsRolls(
            @ForAll long seed,
            @ForAll @IntRange(min = -150, max = 150) int economy,
            @ForAll @IntRange(min = -150, max = 150) int military,
            @ForAll @IntRange(min = -100, max = 100) int society,
            @ForAll @IntRange(min = -100, max = 100) int energy) {
        List<Modifier> modifiers = new ArrayList<>();
        int[] values = {economy, military, society, energy};
        for (TechBranch branch : TechBranch.values()) {
            // Два внески на галузь: сума може виходити за межі переваги, колесо обрізає її.
            int value = values[branch.ordinal()];
            modifiers.add(modifier(branch, "a", value / 2));
            modifiers.add(modifier(branch, "b", value - value / 2));
        }

        StartDevelopment development = DevelopmentWheel.generate(Rng.of(seed), PACK, modifiers);

        assertThat(development.rolls()).hasSize(4);
        int minQuality = 100;
        int maxQuality = 0;
        for (TechBranch branch : TechBranch.values()) {
            int level = development.level(branch);
            RollRecord roll = development.rolls().get(branch.ordinal());
            assertThat(level).isBetween(Development.MIN, Development.MAX);
            assertThat(roll.kind()).isEqualTo(DevelopmentWheel.kind(branch));
            assertThat(roll.resultSectorId()).isEqualTo(DevelopmentWheel.sectorId(level));
            assertThat(roll.advantage()).isEqualTo(Math.clamp(values[branch.ordinal()], -100, 100));
            assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                    .isEqualTo(Wheel.TOTAL_BP);
            assertThat(roll.sectors().getFirst().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
            assertThat(roll.sectors().getLast().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
            minQuality = Math.min(minQuality, QUALITIES[level + 3]);
            maxQuality = Math.max(maxQuality, QUALITIES[level + 3]);
        }
        assertThat(development.quality()).isBetween(minQuality, maxQuality);
    }

    private static Modifier modifier(TechBranch branch, String suffix, int value) {
        return TestDevelopment.wheel(branch, value)
                .toModifier(
                        branch.key() + ":" + suffix, new ModifierSource(SourceKind.EVENT, "test"), null, "event.test");
    }
}
