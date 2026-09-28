package kolo.content.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.Stat;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ModifierTargetsTest {

    @ParameterizedTest
    @EnumSource(Stat.class)
    void everyStatHasSnakeCaseName(Stat stat) {
        assertThat(ModifierTargets.parse("stat:" + stat.name().toLowerCase(Locale.ROOT)))
                .isEqualTo(ModifierTarget.stat(stat));
    }

    @Test
    void wheelTargetTakesAnySnakeCaseKind() {
        assertThat(ModifierTargets.parse("wheel:economic_cycle"))
                .isEqualTo(ModifierTarget.wheel(new WheelKind("economic_cycle")));
        assertThatThrownBy(() -> ModifierTargets.parse("wheel:Economic"))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_KEY_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stat:HDI", "stat:", "hdi", "stats:hdi", "wheel", ""})
    void anythingElseIsUnknown(String text) {
        assertThatThrownBy(() -> ModifierTargets.parse(text))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    @Test
    void nullIsUnknown() {
        assertThatThrownBy(() -> ModifierTargets.parse(null)).isInstanceOf(ValidationException.class);
    }
}
