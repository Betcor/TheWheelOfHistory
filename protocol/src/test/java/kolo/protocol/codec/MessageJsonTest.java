package kolo.protocol.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.nio.charset.StandardCharsets;
import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ProtocolException;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.protocol.TestMessages;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class MessageJsonTest {

    // ---- Формат ----

    @Test
    void helloFormatIsFixed() {
        assertThat(json(MessageJson.write(new ClientMessage.Hello(1, "abc"))))
                .isEqualTo("{\"type\":\"hello\",\"protocol_version\":1,\"content_hash\":\"abc\"}");
    }

    @Test
    void createWorldFormatIsFixed() {
        assertThat(json(MessageJson.write(new ClientMessage.CreateWorld(-5, 3, NpcShare.MANY))))
                .isEqualTo("{\"type\":\"create_world\",\"seed\":-5,\"players\":3,\"npc_share\":\"many\"}");
    }

    @Test
    void readyFormatIsFixed() {
        assertThat(json(MessageJson.write(new ClientMessage.Ready(3)))).isEqualTo("{\"type\":\"ready\",\"turn\":3}");
    }

    @Test
    void phaseFormatIsFixed() {
        assertThat(json(MessageJson.write(new ServerMessage.Phase(12, YearPhase.START_OF_YEAR))))
                .isEqualTo("{\"type\":\"phase\",\"turn\":12,\"phase\":\"start_of_year\"}");
    }

    @Test
    void errorFormatKeepsDetailTypes() {
        ServerMessage.Error error = new ServerMessage.Error(
                ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "players", "value", 17, "flag", true));

        assertThat(json(MessageJson.write(error)))
                .isEqualTo("{\"type\":\"error\",\"code\":\"value_out_of_range\","
                        + "\"details\":{\"field\":\"players\",\"flag\":true,\"value\":17}}");
    }

    @Test
    void cellsWriteAbsentValuesAsNull() {
        MapView map = TestMessages.map(1, 3);
        String json = json(MessageJson.write(MapChunks.split(map, 3).get(1)));

        assertThat(json)
                .startsWith(
                        "{\"type\":\"map_cells\",\"first\":0,\"cells\":[{\"site\":[5,5],\"polygon\":[0,0,10,0,10,10,0,10]")
                .contains("\"kind\":\"sea\",\"terrain\":null");
    }

    // ---- Туди й назад ----

    @Test
    void clientMessagesRoundTrip() {
        for (ClientMessage message : List.of(
                new ClientMessage.Hello(7, TestMessages.HASH),
                new ClientMessage.CreateWorld(Long.MIN_VALUE, 16, NpcShare.FEW),
                new ClientMessage.Ready(0),
                new ClientMessage.Ready(Integer.MAX_VALUE))) {
            assertThat(MessageJson.readClient(MessageJson.write(message))).isEqualTo(message);
        }
    }

    @Test
    void serverMessagesRoundTrip() {
        MapView map = TestMessages.map(Long.MAX_VALUE, 9);
        List<ServerMessage> messages = new java.util.ArrayList<>(MapChunks.split(map, 4));
        messages.add(new ServerMessage.Welcome(1, TestMessages.HASH));
        for (YearPhase phase : YearPhase.values()) {
            messages.add(new ServerMessage.Phase(phase.ordinal() * 100, phase));
        }
        messages.add(new ServerMessage.Error(
                ErrorCode.VERSION_MISMATCH,
                ErrorDetails.of("part", "content", "client", "a'б", "server", Long.MIN_VALUE)));

        for (ServerMessage message : messages) {
            assertThat(MessageJson.readServer(MessageJson.write(message))).isEqualTo(message);
        }
    }

    @Test
    void errorDetailsReadAsLong() {
        ServerMessage read =
                MessageJson.readServer(bytes("{\"type\":\"error\",\"code\":\"not_found\",\"details\":{\"id\":3}}"));

        assertThat(((ServerMessage.Error) read).details()).containsExactly(entry("id", 3L));
    }

    // ---- Суворе читання ----

    @Test
    void rejectsInvalidJson() {
        assertProblem(() -> MessageJson.readClient(bytes("{\"type\":")), "$", "invalid_json");
        assertProblem(() -> MessageJson.readClient(bytes("")), "$", "empty");
        assertProblem(() -> MessageJson.readClient(bytes("{\"type\":\"hello\"} {}")), "$", "invalid_json");
        assertProblem(() -> MessageJson.readClient(bytes("[]")), "$", "expected_object");
    }

    @Test
    void rejectsDuplicateKeys() {
        assertProblem(
                () -> MessageJson.readClient(
                        bytes("{\"type\":\"hello\",\"type\":\"hello\",\"protocol_version\":1,\"content_hash\":\"a\"}")),
                "$",
                "invalid_json");
    }

    @Test
    void rejectsUnknownTypeForItsDirection() {
        byte[] hello = MessageJson.write(new ClientMessage.Hello(1, "a"));
        byte[] welcome = MessageJson.write(new ServerMessage.Welcome(1, "a"));

        assertProblem(() -> MessageJson.readServer(hello), "type", "unknown_type");
        assertProblem(() -> MessageJson.readClient(welcome), "type", "unknown_type");
    }

    @Test
    void rejectsMissingUnknownAndMistypedFields() {
        assertProblem(
                () -> MessageJson.readClient(bytes("{\"type\":\"hello\",\"protocol_version\":1}")),
                "content_hash",
                "missing_field");
        assertProblem(
                () -> MessageJson.readClient(
                        bytes("{\"type\":\"hello\",\"protocol_version\":1,\"content_hash\":\"a\",\"extra\":0}")),
                "extra",
                "unknown_field");
        assertProblem(
                () -> MessageJson.readClient(
                        bytes("{\"type\":\"hello\",\"protocol_version\":\"1\",\"content_hash\":\"a\"}")),
                "protocol_version",
                "expected_int");
        assertProblem(
                () -> MessageJson.readClient(
                        bytes("{\"type\":\"create_world\",\"seed\":1,\"players\":1,\"npc_share\":\"lots\"}")),
                "npc_share",
                "unknown_value");
        assertProblem(
                () -> MessageJson.readServer(bytes("{\"type\":\"phase\",\"turn\":1,\"phase\":\"lunch\"}")),
                "phase",
                "unknown_value");
        assertProblem(() -> MessageJson.readClient(bytes("{\"type\":\"ready\"}")), "turn", "missing_field");
    }

    @Test
    void negativeTurnIsRejected() {
        assertThat(catchProtocol(() -> MessageJson.readClient(bytes("{\"type\":\"ready\",\"turn\":-1}")))
                        .details())
                .containsEntry("cause", "value_out_of_range")
                .containsEntry("field", "turn");
    }

    @Test
    void rejectsNestedProblemsWithLocation() {
        String json = json(
                MessageJson.write(MapChunks.split(TestMessages.map(1, 2), 2).get(1)));

        assertProblem(
                () -> MessageJson.readServer(bytes(json.replace("\"site\":[5,5]", "\"site\":[5]"))),
                "cells[0].site",
                "expected_point");
        assertProblem(
                () -> MessageJson.readServer(bytes(json.replaceFirst("\"river\":true", "\"river\":1"))),
                "cells[0].river",
                "expected_boolean");
    }

    @Test
    void invalidValuesKeepTheirCause() {
        ProtocolException e = catchProtocol(() ->
                MessageJson.readClient(bytes("{\"type\":\"hello\",\"protocol_version\":0,\"content_hash\":\"a\"}")));

        assertThat(e.details())
                .containsEntry("location", "$")
                .containsEntry("cause", "value_out_of_range")
                .containsEntry("field", "protocol_version");
    }

    @Test
    void errorWithoutRequiredDetailsIsRejected() {
        ProtocolException e = catchProtocol(() -> MessageJson.readServer(
                bytes("{\"type\":\"error\",\"code\":\"version_mismatch\",\"details\":{\"part\":\"protocol\"}}")));

        assertThat(e.details()).containsEntry("cause", "missing_definition");
    }

    @Test
    void errorDetailsMustBeSimpleValues() {
        assertProblem(
                () -> MessageJson.readServer(
                        bytes("{\"type\":\"error\",\"code\":\"not_found\",\"details\":{\"id\":1.5}}")),
                "details.id",
                "expected_long");
        assertProblem(
                () -> MessageJson.readServer(bytes("{\"type\":\"error\",\"code\":\"no_such_code\",\"details\":{}}")),
                "code",
                "unknown_value");
    }

    // ---- Допоміжне ----

    private static void assertProblem(ThrowingCallable read, String location, String problem) {
        assertThat(catchProtocol(read).details())
                .containsEntry("location", location)
                .containsEntry("problem", problem);
    }

    private static ProtocolException catchProtocol(ThrowingCallable read) {
        ProtocolException[] caught = new ProtocolException[1];
        assertThatThrownBy(read).isInstanceOfSatisfying(ProtocolException.class, e -> caught[0] = e);
        assertThat(caught[0].code()).isEqualTo(ErrorCode.PROTOCOL_ERROR);
        return caught[0];
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String json(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
