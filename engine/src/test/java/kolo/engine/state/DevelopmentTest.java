package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class DevelopmentTest {

    @Test
    void levelsMatchDesignScale() {
        // GD §4.3: від глибокого відставання (−3) до лідера (+2).
        assertThat(Development.levels()).containsExactly(-3, -2, -1, 0, 1, 2);
        assertThat(Development.levels()).contains(Development.WORLD);
    }

    @Test
    void checkRejectsLevelsOutsideScale() {
        assertThat(Development.check("level", Development.MAX)).isEqualTo(2);
        assertThatThrownBy(() -> Development.check("level", Development.MIN - 1))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @Test
    void contentKeysAreSnakeCase() {
        assertThat(TechBranch.ENERGY_SCIENCE.key()).isEqualTo("energy_science");
        assertThat(NuclearStatus.ARSENAL.key()).isEqualTo("arsenal");
    }
}
