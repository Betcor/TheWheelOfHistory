package kolo.engine.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ChecksSnakeCaseTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "democracy", "sub_ideology_2", "x9", "trailing_"})
    void acceptsSnakeCase(String key) {
        assertThat(Checks.snakeCase("id", key)).isEqualTo(key);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Democracy", "camelCase", "_lead", "9lives", "with-dash", "with space", "демократія"})
    void rejectsEverythingElse(String key) {
        assertThatThrownBy(() -> Checks.snakeCase("ideology.id", key))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INVALID_KEY_FORMAT);
                    assertThat(e.details())
                            .containsExactly(entry("field", "ideology.id"), entry("value", String.valueOf(key)));
                });
    }
}
