package kolo.protocol.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalInt;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class NicknamesTest {

    @Test
    void acceptsOrdinaryNicknames() {
        assertThat(Nicknames.check("nickname", "Оля")).isEqualTo("Оля");
        assertThat(Nicknames.check("nickname", "Гравець 2")).isEqualTo("Гравець 2");
        // Межа — у символах Unicode, а не в char: емодзі — один символ із двох char.
        String longest = "😀".repeat(Nicknames.MAX_LENGTH);
        assertThat(Nicknames.check("nickname", longest)).isEqualTo(longest);
    }

    @Test
    void rejectsBlankLongAndOddNicknames() {
        assertCode("", ErrorCode.BLANK_VALUE);
        assertCode("   ", ErrorCode.BLANK_VALUE);
        assertCode("я".repeat(Nicknames.MAX_LENGTH + 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertCode(" Оля", ErrorCode.INVALID_NAME_FORMAT);
        assertCode("Оля ", ErrorCode.INVALID_NAME_FORMAT);
        assertCode("О\nля", ErrorCode.INVALID_NAME_FORMAT);
    }

    @Test
    void keyIgnoresCase() {
        assertThat(Nicknames.key("Оля")).isEqualTo(Nicknames.key("оЛЯ"));
        assertThat(Nicknames.key("Оля")).isNotEqualTo(Nicknames.key("Оль"));
    }

    @Test
    void playerInfoChecksItsFields() {
        assertThatThrownBy(() -> new PlayerInfo(0, "a", false, true, false, OptionalInt.empty()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new PlayerInfo(1, "a", false, true, false, OptionalInt.of(-1)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new PlayerInfo(1, " a", false, true, false, OptionalInt.empty()))
                .isInstanceOf(ValidationException.class);
    }

    private static void assertCode(String nickname, ErrorCode code) {
        assertThatThrownBy(() -> Nicknames.check("nickname", nickname))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
