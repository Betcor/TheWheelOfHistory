package kolo.engine.modifier;

import static kolo.engine.modifier.TestModifiers.onStat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.state.Stat;
import org.junit.jupiter.api.Test;

class ModifierTest {

    @Test
    void permanentModifierIsAlwaysActive() {
        Modifier modifier = onStat("reform", Stat.STABILITY, 5, null);

        assertThat(modifier.isActiveAt(0)).isTrue();
        assertThat(modifier.isActiveAt(1_000)).isTrue();
    }

    @Test
    void modifierIsActiveThroughItsLastTurnInclusive() {
        Modifier modifier = onStat("scandal", Stat.STABILITY, -10, 7);

        assertThat(modifier.isActiveAt(6)).isTrue();
        assertThat(modifier.isActiveAt(7)).isTrue();
        assertThat(modifier.isActiveAt(8)).isFalse();
    }

    @Test
    void rejectsInvalidFields() {
        ModifierSource source = new ModifierSource(SourceKind.EVENT, "evt_1");
        ModifierTarget target = ModifierTarget.stat(Stat.HDI);

        assertThatThrownBy(() -> new Modifier(" ", source, target, 1, null, "modifier.x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Modifier("m", null, target, 1, null, "modifier.x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Modifier("m", source, null, 1, null, "modifier.x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Modifier("m", source, target, 1, -1, "modifier.x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Modifier("m", source, target, 1, null, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModifierSource(SourceKind.TECH, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModifierTarget.stat(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModifierTarget.wheel(null)).isInstanceOf(NullPointerException.class);
    }
}
