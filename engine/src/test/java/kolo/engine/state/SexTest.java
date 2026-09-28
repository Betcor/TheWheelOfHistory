package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SexTest {

    @Test
    void keysAndGenders() {
        assertThat(Arrays.stream(Sex.values()).map(Sex::key)).containsExactly("male", "female");
        assertThat(Sex.MALE.gender()).isEqualTo(GrammaticalGender.MASCULINE);
        assertThat(Sex.FEMALE.gender()).isEqualTo(GrammaticalGender.FEMININE);
    }
}
