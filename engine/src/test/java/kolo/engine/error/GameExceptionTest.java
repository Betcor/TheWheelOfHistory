package kolo.engine.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.LinkedHashMap;
import java.util.Map;
import kolo.engine.state.Season;
import org.junit.jupiter.api.Test;

class GameExceptionTest {

    @Test
    void detailsAreSortedByKeyAndImmutable() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("value", 120);
        source.put("field", "stability");
        source.put("max", 100);

        ValidationException e = new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, source);
        source.put("min", 0);

        assertThat(e.details()).containsExactly(entry("field", "stability"), entry("max", 100), entry("value", 120));
        assertThatThrownBy(() -> e.details().put("min", 0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nonPrimitiveDetailValuesBecomeStrings() {
        ValidationException e = new ValidationException(
                ErrorCode.UNKNOWN_REFERENCE,
                ErrorDetails.of("season", Season.WINTER, "missing", null, "flag", true, "count", 3L));

        assertThat(e.details())
                .containsExactly(
                        entry("count", 3L), entry("flag", true), entry("missing", "null"), entry("season", "WINTER"));
    }

    @Test
    void messageStartsWithI18nKeyAndKeepsCause() {
        IllegalStateException cause = new IllegalStateException("sqlite");
        SaveFileException e =
                new SaveFileException(ErrorCode.SAVE_FILE_ERROR, ErrorDetails.of("file", "a.kolo"), cause);

        assertThat(e).hasMessage("error.save_file_error {file=a.kolo}").hasCause(cause);
        assertThat(e).isInstanceOf(RuntimeException.class);
    }

    @Test
    void leafExceptionsCarryTheirFixedCode() {
        assertThat(new InsufficientFundsException(ErrorDetails.of()).code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(new InvalidOrderException(ErrorDetails.of()).code()).isEqualTo(ErrorCode.INVALID_ORDER);
        assertThat(new SaveVersionException(ErrorDetails.of()).code()).isEqualTo(ErrorCode.SAVE_VERSION_TOO_NEW);
    }

    @Test
    void rejectsCodeFromAnotherBranch() {
        assertThatThrownBy(() -> new ValidationException(ErrorCode.INSUFFICIENT_FUNDS, ErrorDetails.of()))
                .isInstanceOf(IllegalArgumentException.class);
        // Код підкласу не можна поставити на загальніший виняток: клієнт розрізняє їх за кодом.
        assertThatThrownBy(() -> new RuleViolationException(ErrorCode.INSUFFICIENT_FUNDS, ErrorDetails.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidationException(null, ErrorDetails.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void errorDetailsRejectMalformedPairs() {
        assertThatThrownBy(() -> ErrorDetails.of("field")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ErrorDetails.of(1, "value")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ErrorDetails.of("b", 1, "a", 2)).containsExactly(entry("a", 2), entry("b", 1));
    }

    @Test
    void checksProduceStandardDetails() {
        assertThat(Checks.inRange("x", 5, 0, 10)).isEqualTo(5);
        assertThat(Checks.notBlank("name", "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> Checks.inRange("x", 11L, 0, 10))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details())
                            .containsExactly(
                                    entry("field", "x"), entry("max", 10L), entry("min", 0L), entry("value", 11L));
                });
        assertThatThrownBy(() -> Checks.notBlank("name", " ")).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.BLANK_VALUE);
            assertThat(e.details()).containsExactly(entry("field", "name"));
        });
        assertThatThrownBy(() -> Checks.notBlank("name", null)).isInstanceOf(ValidationException.class);
    }
}
