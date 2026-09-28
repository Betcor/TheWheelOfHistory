package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CountryIdTest {

    @Test
    void buildsFromNumber() {
        assertThat(CountryId.of(17)).isEqualTo(new CountryId("cty_17"));
        assertThat(CountryId.of(17)).hasToString("cty_17");
    }

    @Test
    void ordersByValue() {
        assertThat(CountryId.of(1)).isLessThan(CountryId.of(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "cty_", "cty_-1", "cty_1a", "prv_1", "CTY_1", " cty_1"})
    void rejectsMalformedIds(String value) {
        assertValidation(() -> new CountryId(value), ErrorCode.INVALID_KEY_FORMAT);
    }

    @Test
    void rejectsNull() {
        assertValidation(() -> new CountryId(null), ErrorCode.INVALID_KEY_FORMAT);
    }

    private static void assertValidation(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
