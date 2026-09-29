package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class TrainingTest {

    @Test
    void levelsMatchDesignScale() {
        // GD §4.4: п'ять рівнів від ополчення до еліти, регулярна армія — посередині.
        assertThat(Training.levels()).containsExactly(1, 2, 3, 4, 5);
        assertThat(Training.levels()).contains(Training.REGULAR);
    }

    @Test
    void checkRejectsLevelsOutsideScale() {
        assertThat(Training.check("level", Training.MAX)).isEqualTo(5);
        for (int level : new int[] {Training.MIN - 1, Training.MAX + 1}) {
            assertThatThrownBy(() -> Training.check("level", level))
                    .isInstanceOfSatisfying(
                            ValidationException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        }
    }
}
