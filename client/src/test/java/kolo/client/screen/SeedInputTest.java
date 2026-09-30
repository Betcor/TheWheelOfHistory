package kolo.client.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class SeedInputTest {

    @Test
    void emptyMeansRandom() {
        assertThat(SeedInput.parse("")).isEqualTo(OptionalLong.empty());
        assertThat(SeedInput.parse("   ")).isEqualTo(OptionalLong.empty());
        assertThat(SeedInput.parse(null)).isEqualTo(OptionalLong.empty());
    }

    @Test
    void parsesSignedLong() {
        assertThat(SeedInput.parse(" 1970 ")).hasValue(1970);
        assertThat(SeedInput.parse("-42")).hasValue(-42);
        assertThat(SeedInput.parse(String.valueOf(Long.MAX_VALUE))).hasValue(Long.MAX_VALUE);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> SeedInput.parse("abc")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> SeedInput.parse("1.5")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> SeedInput.parse("99999999999999999999")).isInstanceOf(NumberFormatException.class);
    }
}
