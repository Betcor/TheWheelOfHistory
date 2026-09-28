package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.Stat;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModifierDefTest {

    private static final ModifierTarget ECONOMY = ModifierTarget.wheel(new WheelKind("economic_cycle"));

    @ParameterizedTest
    @ValueSource(ints = {-100, 0, 100})
    void wheelModifierFitsAdvantageRange(int value) {
        assertThat(new ModifierDef(ECONOMY, value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {-101, 101})
    void wheelModifierBeyondAdvantageIsRejected(int value) {
        assertThatThrownBy(() -> new ModifierDef(ECONOMY, value))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details()).contains(entry("min", -100L), entry("max", 100L));
                });
    }

    @Test
    void statModifierIsBoundedBySpanOfStat() {
        ModifierTarget stability = ModifierTarget.stat(Stat.STABILITY);
        assertThat(new ModifierDef(stability, -100).value()).isEqualTo(-100);
        assertThat(new ModifierDef(stability, 100).value()).isEqualTo(100);
        assertThatThrownBy(() -> new ModifierDef(stability, 101)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new ModifierDef(stability, -101)).isInstanceOf(ValidationException.class);

        // Для необмежених показників межа — діапазон int.
        assertThat(new ModifierDef(ModifierTarget.stat(Stat.GDP), Integer.MAX_VALUE).value())
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void becomesModifierWithGivenSourceAndTerm() {
        ModifierDef def = new ModifierDef(ModifierTarget.stat(Stat.HDI), 5);
        ModifierSource source = new ModifierSource(SourceKind.IDEOLOGY, "democracy");

        Modifier modifier = def.toModifier("ideology:democracy:0", source, 12, "ideology.democracy");

        assertThat(modifier)
                .isEqualTo(new Modifier(
                        "ideology:democracy:0", source, ModifierTarget.stat(Stat.HDI), 5, 12, "ideology.democracy"));
    }
}
