package kolo.protocol.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.NotFoundException;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Season;
import kolo.protocol.TestMessages;
import org.junit.jupiter.api.Test;

class ServerMessageTest {

    @Test
    void errorNormalizesDetailsForTheWire() {
        TreeMap<String, Object> details = new TreeMap<>();
        details.put("int", 3);
        details.put("short", (short) 4);
        details.put("long", 5L);
        details.put("text", "т");
        details.put("flag", false);
        details.put("season", Season.WINTER);
        details.put("fraction", 1.5);

        ServerMessage.Error error = new ServerMessage.Error(ErrorCode.NOT_FOUND, details);

        assertThat(error.details())
                .containsExactly(
                        entry("flag", false),
                        entry("fraction", "1.5"),
                        entry("int", 3L),
                        entry("long", 5L),
                        entry("season", "WINTER"),
                        entry("short", 4L),
                        entry("text", "т"));
        assertThatThrownBy(() -> error.details().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void errorFromExceptionKeepsCodeAndDetails() {
        ServerMessage.Error error =
                ServerMessage.Error.of(new NotFoundException(ErrorCode.NOT_FOUND, ErrorDetails.of("id", "cty_4")));

        assertThat(error.code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(error.details()).containsExactly(entry("id", "cty_4"));
    }

    @Test
    void errorRequiresMandatoryDetails() {
        assertThatThrownBy(() -> new ServerMessage.Error(ErrorCode.SAVE_VERSION_TOO_NEW, ErrorDetails.of("version", 2)))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.details()).containsEntry("value", "supported"));
    }

    @Test
    void mapMessagesValidateShape() {
        assertThatThrownBy(() -> new ServerMessage.MapCells(0, List.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() ->
                        new ServerMessage.MapCells(-1, TestMessages.map(1, 1).cells()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new ServerMessage.MapStart(1, 1, 1, 0, List.of()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new ClientMessage.Hello(1, " ")).isInstanceOf(ValidationException.class);
    }
}
