package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TreeSet;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakWheelDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/** Властивості коліс стріків на довільних seed. */
class StreakWheelPropertiesTest {

    @Property
    void sameSeedGivesSameBonus(@ForAll long seed, @ForAll StreakKind kind) {
        assertThat(StreakWheel.generate(Rng.of(seed), TestRegime.PACK, kind))
                .isEqualTo(StreakWheel.generate(Rng.of(seed), TestRegime.PACK, kind));
    }

    @Property
    void bonusIsConsistentWithItsWheel(@ForAll long seed, @ForAll StreakKind kind) {
        StreakWheelDef wheel = TestRegime.PACK.streaks().wheel(kind);
        StreakBonus bonus = StreakWheel.generate(Rng.of(seed), TestRegime.PACK, kind);

        assertThat(wheel.rewards()).contains(bonus.reward());
        assertThat(bonus.roll().roll()).isBetween(0, Wheel.TOTAL_BP - 1);
        TreeSet<String> expected = new TreeSet<>(wheel.tags());
        expected.addAll(bonus.reward().tags());
        assertThat(bonus.tags()).containsExactlyElementsOf(expected);
        assertThat(bonus.modifiers()).hasSameSizeAs(bonus.reward().modifiers());
        assertThat(bonus.modifiers()).extracting(Modifier::id).doesNotHaveDuplicates();
    }
}
