package kolo.protocol.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ProtocolException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.engine.view.MapView;
import kolo.protocol.TestMessages;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.PlayerToken;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.WorldInfo;
import kolo.protocol.message.YearPhase;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class MessageJsonTest {

    private static final TurnTimer LIVE_3 = new TurnTimer(TurnTimer.Mode.LIVE, 180);
    private static final TurnTimer ASYNC_12 = new TurnTimer(TurnTimer.Mode.ASYNC, 43_200);

    // ---- Формат ----

    @Test
    void helloFormatIsFixed() {
        assertThat(json(MessageJson.write(new ClientMessage.Hello(1, "abc"))))
                .isEqualTo("{\"type\":\"hello\",\"protocol_version\":1,\"content_hash\":\"abc\"}");
    }

    @Test
    void createLobbyFormatIsFixed() {
        assertThat(json(MessageJson.write(new ClientMessage.CreateLobby("Оля", -5, NpcShare.MANY))))
                .isEqualTo("{\"type\":\"create_lobby\",\"nickname\":\"Оля\",\"seed\":-5,\"npc_share\":\"many\"}");
    }

    @Test
    void lobbyRequestsFormatIsFixed() {
        assertThat(json(MessageJson.write(new ClientMessage.ListLobbies()))).isEqualTo("{\"type\":\"list_lobbies\"}");
        assertThat(json(MessageJson.write(new ClientMessage.JoinLobby(4, "Ігор"))))
                .isEqualTo("{\"type\":\"join_lobby\",\"session\":4,\"nickname\":\"Ігор\"}");
        assertThat(json(MessageJson.write(new ClientMessage.StartGame()))).isEqualTo("{\"type\":\"start_game\"}");
        assertThat(json(MessageJson.write(new ClientMessage.Rejoin(4, 2, "ab12"))))
                .isEqualTo("{\"type\":\"rejoin\",\"session\":4,\"player\":2,\"token\":\"ab12\"}");
        assertThat(json(MessageJson.write(new ClientMessage.Leave()))).isEqualTo("{\"type\":\"leave\"}");
    }

    @Test
    void worldRequestsFormatIsFixed() {
        assertThat(json(MessageJson.write(new ClientMessage.ListWorlds()))).isEqualTo("{\"type\":\"list_worlds\"}");
        assertThat(json(MessageJson.write(new ClientMessage.LoadWorld("world-5", "Оля", Optional.empty()))))
                .isEqualTo("{\"type\":\"load_world\",\"world\":\"world-5\",\"nickname\":\"Оля\",\"seat\":null}");
        assertThat(json(MessageJson.write(
                        new ClientMessage.LoadWorld("world-5", "Оля", Optional.of(new PlayerToken(2, "ab12"))))))
                .isEqualTo("{\"type\":\"load_world\",\"world\":\"world-5\",\"nickname\":\"Оля\","
                        + "\"seat\":{\"player\":2,\"token\":\"ab12\"}}");
        assertThat(json(MessageJson.write(new ClientMessage.AssignSeat(5, 2))))
                .isEqualTo("{\"type\":\"assign_seat\",\"guest\":5,\"seat\":2}");
    }

    @Test
    void worldsFormatIsFixed() {
        ServerMessage.Worlds worlds = new ServerMessage.Worlds(List.of(
                new WorldInfo("world-5", Optional.of(TestMessages.WORLD), 5, 3, List.of("Оля", "Ігор")),
                new WorldInfo("old", Optional.empty(), -1, 0, List.of())));

        assertThat(json(MessageJson.write(worlds)))
                .isEqualTo("{\"type\":\"worlds\",\"worlds\":[{\"name\":\"world-5\",\"key\":\"" + TestMessages.WORLD
                        + "\",\"seed\":5,\"turn\":3,\"players\":[\"Оля\",\"Ігор\"]},"
                        + "{\"name\":\"old\",\"key\":null,\"seed\":-1,\"turn\":0,\"players\":[]}]}");
    }

    @Test
    void savedLobbyFormatIsFixed() {
        ServerMessage.Lobby lobby = new ServerMessage.Lobby(
                3,
                TestMessages.WORLD,
                new LobbySetup.SavedWorld("world-5", 5, 7, LIVE_3),
                List.of(
                        new PlayerInfo(1, "Оля", false, false, false, OptionalInt.of(0)),
                        new PlayerInfo(3, "Ігор", true, true, false, OptionalInt.empty())),
                List.of(TurnTimer.MANUAL, LIVE_3));

        assertThat(json(MessageJson.write(lobby)))
                .isEqualTo("{\"type\":\"lobby\",\"session\":3,\"world\":\"" + TestMessages.WORLD + "\","
                        + "\"setup\":{\"kind\":\"saved_world\",\"name\":\"world-5\",\"seed\":5,\"turn\":7,"
                        + "\"timer\":{\"mode\":\"live\",\"seconds\":180}},"
                        + "\"players\":["
                        + "{\"number\":1,\"nickname\":\"Оля\",\"host\":false,\"connected\":false,\"ready\":false,"
                        + "\"country\":0},"
                        + "{\"number\":3,\"nickname\":\"Ігор\",\"host\":true,\"connected\":true,\"ready\":false,"
                        + "\"country\":null}],"
                        + "\"timers\":[{\"mode\":\"manual\",\"seconds\":0},{\"mode\":\"live\",\"seconds\":180}]}");
    }

    @Test
    void lobbyFormatIsFixed() {
        ServerMessage.Lobby lobby = new ServerMessage.Lobby(
                3,
                TestMessages.WORLD,
                new LobbySetup.NewWorld(9, NpcShare.FEW, TurnTimer.MANUAL),
                List.of(
                        new PlayerInfo(1, "Оля", true, true, false, OptionalInt.empty()),
                        new PlayerInfo(4, "Ігор", false, true, false, OptionalInt.empty())),
                List.of(TurnTimer.MANUAL));

        assertThat(json(MessageJson.write(lobby)))
                .isEqualTo("{\"type\":\"lobby\",\"session\":3,\"world\":\"" + TestMessages.WORLD + "\","
                        + "\"setup\":{\"kind\":\"new_world\",\"seed\":9,\"npc_share\":\"few\","
                        + "\"timer\":{\"mode\":\"manual\",\"seconds\":0}},\"players\":["
                        + "{\"number\":1,\"nickname\":\"Оля\",\"host\":true,\"connected\":true,\"ready\":false,"
                        + "\"country\":null},"
                        + "{\"number\":4,\"nickname\":\"Ігор\",\"host\":false,\"connected\":true,\"ready\":false,"
                        + "\"country\":null}],\"timers\":[{\"mode\":\"manual\",\"seconds\":0}]}");
    }

    @Test
    void playersJoinedAndLobbiesFormatIsFixed() {
        assertThat(json(MessageJson.write(new ServerMessage.Players(
                        List.of(new PlayerInfo(2, "Оля", false, false, true, OptionalInt.of(1)))))))
                .isEqualTo("{\"type\":\"players\",\"players\":[{\"number\":2,\"nickname\":\"Оля\",\"host\":false,"
                        + "\"connected\":false,\"ready\":true,\"country\":1}]}");
        assertThat(json(MessageJson.write(new ServerMessage.Joined(3, "k1", 2, "ab12"))))
                .isEqualTo("{\"type\":\"joined\",\"session\":3,\"world\":\"k1\",\"player\":2,\"token\":\"ab12\"}");
        assertThat(json(MessageJson.write(new ServerMessage.Lobbies(List.of(
                        new LobbyInfo(3, "k1", "Оля", 2, new LobbySetup.NewWorld(4, NpcShare.NORMAL, ASYNC_12)))))))
                .isEqualTo("{\"type\":\"lobbies\",\"lobbies\":[{\"session\":3,\"world\":\"k1\",\"host\":\"Оля\","
                        + "\"players\":2,\"setup\":{\"kind\":\"new_world\",\"seed\":4,\"npc_share\":\"normal\","
                        + "\"timer\":{\"mode\":\"async\",\"seconds\":43200}}}]}");
    }

    @Test
    void readyFormatIsFixed() {
        assertThat(json(MessageJson.write(new ClientMessage.Ready(3)))).isEqualTo("{\"type\":\"ready\",\"turn\":3}");
    }

    @Test
    void timerEndYearAndResumeFormatIsFixed() {
        assertThat(json(MessageJson.write(new ClientMessage.SetTimer(LIVE_3))))
                .isEqualTo("{\"type\":\"set_timer\",\"timer\":{\"mode\":\"live\",\"seconds\":180}}");
        assertThat(json(MessageJson.write(new ClientMessage.EndYear(4))))
                .isEqualTo("{\"type\":\"end_year\",\"turn\":4}");
        assertThat(json(MessageJson.write(new ClientMessage.Resume(4)))).isEqualTo("{\"type\":\"resume\",\"turn\":4}");
    }

    @Test
    void phaseFormatIsFixed() {
        assertThat(json(MessageJson.write(new ServerMessage.Phase(12, YearPhase.START_OF_YEAR))))
                .isEqualTo("{\"type\":\"phase\",\"turn\":12,\"phase\":\"start_of_year\",\"time_left_millis\":null}");
        assertThat(json(MessageJson.write(new ServerMessage.Phase(12, YearPhase.ORDERS, OptionalLong.of(90_000)))))
                .isEqualTo("{\"type\":\"phase\",\"turn\":12,\"phase\":\"orders\",\"time_left_millis\":90000}");
        assertThat(json(MessageJson.write(new ServerMessage.Phase(12, YearPhase.PAUSED))))
                .isEqualTo("{\"type\":\"phase\",\"turn\":12,\"phase\":\"paused\",\"time_left_millis\":null}");
    }

    @Test
    void generationPhaseFormatIsFixed() {
        assertThat(json(MessageJson.write(new ServerMessage.Phase(0, YearPhase.GENERATION))))
                .isEqualTo("{\"type\":\"phase\",\"turn\":0,\"phase\":\"generation\",\"time_left_millis\":null}");
    }

    @Test
    void ownCountryFormatIsFixed() {
        String full = json(MessageJson.write(new ServerMessage.OwnCountry(TestMessages.card(0, 7))));
        String secular = json(MessageJson.write(new ServerMessage.OwnCountry(TestMessages.card(1, 7))));

        assertThat(full)
                .startsWith("{\"type\":\"own_country\",\"card\":{\"number\":0,\"name\":{")
                .contains(
                        "\"capital\":794,\"provinces\":87,\"population_k\":6007,"
                                + "\"ideology\":\"theocracy\",\"sub_ideology\":\"temple_state\",\"religion\":{\"id\":\"rel_3\",")
                .contains(
                        "\"holy_center\":391},\"world_religions\":[{\"gender\":\"feminine\",\"forms\":[\"Рід Батая\",")
                .contains("\"development\":{\"economy\":2,\"military\":0,\"society\":-1,\"energy_science\":1}")
                .contains("\"nuclear\":\"arsenal\",\"warheads\":12,\"fate_tokens\":0,"
                        + "\"deposits\":[{\"province\":794,\"resource\":\"fertile_land\"},")
                .contains("\"backstory_neighbor\":4,\"streaks\":[{\"kind\":\"golden_age\",\"reward\":\"fate_token\"}]")
                .contains("{\"id\":\"ideology:theocracy:0\",\"source_kind\":\"ideology\",\"source_ref\":\"theocracy\","
                        + "\"target\":\"stat:stability\",\"value\":10,\"expires_at_turn\":null,"
                        + "\"description_key\":\"ideology.theocracy\"}")
                .contains("\"target\":\"wheel:generation_hdi\",\"value\":-3,\"expires_at_turn\":9,")
                .contains("{\"kind\":\"generation_area\",\"sectors\":[{\"id\":\"small\",\"weight_bp\":2500,"
                        + "\"tier\":\"partial\",\"quality\":30},")
                .contains("\"result\":\"large\",\"roll\":7,\"turn\":0,\"season\":\"winter\"}")
                .endsWith("\"season\":null}]}}");
        assertThat(secular)
                .contains("\"religion\":null,")
                .contains("\"backstory\":[],\"backstory_neighbor\":null,\"streaks\":[],");
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
                new ClientMessage.ListLobbies(),
                new ClientMessage.CreateLobby("Гравець 1", Long.MIN_VALUE, NpcShare.FEW),
                new ClientMessage.JoinLobby(Long.MAX_VALUE, "x"),
                new ClientMessage.StartGame(),
                new ClientMessage.Rejoin(1, Integer.MAX_VALUE, TestMessages.HASH),
                new ClientMessage.Leave(),
                new ClientMessage.Ready(0),
                new ClientMessage.Ready(Integer.MAX_VALUE),
                new ClientMessage.ListWorlds(),
                new ClientMessage.LoadWorld("світ «1»", "x", Optional.empty()),
                new ClientMessage.LoadWorld(
                        "w", "x", Optional.of(new PlayerToken(Integer.MAX_VALUE, TestMessages.HASH))),
                new ClientMessage.AssignSeat(Integer.MAX_VALUE, 1),
                new ClientMessage.SetTimer(TurnTimer.MANUAL),
                new ClientMessage.SetTimer(new TurnTimer(TurnTimer.Mode.ASYNC, TurnTimer.MAX_SECONDS)),
                new ClientMessage.EndYear(Integer.MAX_VALUE),
                new ClientMessage.Resume(0))) {
            assertThat(MessageJson.readClient(MessageJson.write(message))).isEqualTo(message);
        }
    }

    @Test
    void serverMessagesRoundTrip() {
        MapView map = TestMessages.map(Long.MAX_VALUE, 9);
        List<ServerMessage> messages = new java.util.ArrayList<>(MapChunks.split(map, 4));
        messages.add(new ServerMessage.Welcome(1, TestMessages.HASH));
        messages.add(new ServerMessage.Lobbies(List.of()));
        messages.add(new ServerMessage.Joined(1, TestMessages.WORLD, 1, TestMessages.HASH));
        messages.add(new ServerMessage.Worlds(List.of()));
        messages.add(new ServerMessage.Worlds(
                List.of(new WorldInfo("w", Optional.empty(), Long.MIN_VALUE, Integer.MAX_VALUE, List.of("a")))));
        messages.add(new ServerMessage.Lobbies(List.of(new LobbyInfo(
                Long.MAX_VALUE,
                TestMessages.WORLD,
                "a",
                1,
                new LobbySetup.SavedWorld("w", Long.MIN_VALUE, 0, ASYNC_12)))));
        messages.add(new ServerMessage.Lobby(
                1,
                TestMessages.WORLD,
                new LobbySetup.NewWorld(Long.MAX_VALUE, NpcShare.MANY, LIVE_3),
                List.of(new PlayerInfo(1, "a", true, true, false, OptionalInt.empty())),
                List.of(LIVE_3, ASYNC_12)));
        messages.add(new ServerMessage.Players(List.of(new PlayerInfo(1, "a", true, true, true, OptionalInt.of(0)))));
        for (YearPhase phase : YearPhase.values()) {
            messages.add(new ServerMessage.Phase(phase.ordinal() * 100, phase));
        }
        messages.add(new ServerMessage.Phase(Integer.MAX_VALUE, YearPhase.ORDERS, OptionalLong.of(Long.MAX_VALUE)));
        messages.add(new ServerMessage.Phase(0, YearPhase.ORDERS, OptionalLong.of(0)));
        messages.add(new ServerMessage.OwnCountry(TestMessages.card(0, 7)));
        messages.add(new ServerMessage.OwnCountry(TestMessages.card(1, -7)));
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
                        bytes("{\"type\":\"create_lobby\",\"nickname\":\"a\",\"seed\":1,\"npc_share\":\"lots\"}")),
                "npc_share",
                "unknown_value");
        assertProblem(
                () -> MessageJson.readServer(bytes("{\"type\":\"phase\",\"turn\":1,\"phase\":\"lunch\"}")),
                "phase",
                "unknown_value");
        assertProblem(() -> MessageJson.readClient(bytes("{\"type\":\"ready\"}")), "turn", "missing_field");
    }

    @Test
    void messagesWithoutFieldsRejectExtraFields() {
        assertProblem(
                () -> MessageJson.readClient(bytes("{\"type\":\"start_game\",\"now\":true}")), "now", "unknown_field");
    }

    @Test
    void invalidNicknamesAreRejected() {
        assertThat(catchProtocol(() -> MessageJson.readClient(
                                bytes("{\"type\":\"join_lobby\",\"session\":1,\"nickname\":\" Оля\"}")))
                        .details())
                .containsEntry("cause", "invalid_name_format")
                .containsEntry("field", "nickname");
        assertThat(catchProtocol(() -> MessageJson.readClient(bytes(
                                "{\"type\":\"join_lobby\",\"session\":1,\"nickname\":\"" + "я".repeat(25) + "\"}")))
                        .details())
                .containsEntry("cause", "value_out_of_range");
    }

    @Test
    void unknownSetupKindIsRejected() {
        String json = "{\"type\":\"lobby\",\"session\":3,\"world\":\"k\",\"setup\":{\"kind\":\"old_world\"},"
                + "\"players\":[]}";

        assertProblem(() -> MessageJson.readServer(bytes(json)), "setup.kind", "unknown_value");
    }

    @Test
    void seatMustHaveOnlyPlayerAndToken() {
        assertProblem(
                () -> MessageJson.readClient(bytes("{\"type\":\"load_world\",\"world\":\"w\",\"nickname\":\"a\","
                        + "\"seat\":{\"player\":1,\"token\":\"t\",\"host\":true}}")),
                "seat.host",
                "unknown_field");
        assertThat(catchProtocol(() -> MessageJson.readClient(bytes("{\"type\":\"load_world\",\"world\":\"w\","
                                + "\"nickname\":\"a\",\"seat\":{\"player\":0,\"token\":\"t\"}}")))
                        .details())
                .containsEntry("location", "seat")
                .containsEntry("field", "player");
    }

    @Test
    void lobbyWithoutExactlyOneHostIsRejected() {
        String json = "{\"type\":\"lobby\",\"session\":3,\"world\":\"k\","
                + "\"setup\":{\"kind\":\"new_world\",\"seed\":9,\"npc_share\":\"few\","
                + "\"timer\":{\"mode\":\"manual\",\"seconds\":0}},\"players\":["
                + "{\"number\":1,\"nickname\":\"Оля\",\"host\":false,\"connected\":true,\"ready\":false,"
                + "\"country\":null}],\"timers\":[{\"mode\":\"manual\",\"seconds\":0}]}";

        assertThat(catchProtocol(() -> MessageJson.readServer(bytes(json))).details())
                .containsEntry("cause", "value_out_of_range")
                .containsEntry("field", "hosts");
    }

    @Test
    void timerMustBeValid() {
        assertProblem(
                () -> MessageJson.readClient(
                        bytes("{\"type\":\"set_timer\",\"timer\":{\"mode\":\"slow\",\"seconds\":1}}")),
                "timer.mode",
                "unknown_value");
        assertThat(catchProtocol(() -> MessageJson.readClient(
                                bytes("{\"type\":\"set_timer\",\"timer\":{\"mode\":\"manual\",\"seconds\":5}}")))
                        .details())
                .containsEntry("location", "timer")
                .containsEntry("cause", "conflicting_fields");
        assertThat(catchProtocol(() -> MessageJson.readServer(
                                bytes("{\"type\":\"phase\",\"turn\":1,\"phase\":\"report\",\"time_left_millis\":5}")))
                        .details())
                .containsEntry("cause", "conflicting_fields");
    }

    @Test
    void lobbyTimerMustBeAmongChoices() {
        String json = "{\"type\":\"lobby\",\"session\":3,\"world\":\"k\","
                + "\"setup\":{\"kind\":\"new_world\",\"seed\":9,\"npc_share\":\"few\","
                + "\"timer\":{\"mode\":\"live\",\"seconds\":60}},\"players\":["
                + "{\"number\":1,\"nickname\":\"Оля\",\"host\":true,\"connected\":true,\"ready\":false,"
                + "\"country\":null}],\"timers\":[{\"mode\":\"manual\",\"seconds\":0}]}";

        assertThat(catchProtocol(() -> MessageJson.readServer(bytes(json))).details())
                .containsEntry("cause", "unknown_reference")
                .containsEntry("field", "timers");
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
    void cardIsReadStrictly() {
        String json = json(MessageJson.write(new ServerMessage.OwnCountry(TestMessages.card(0, 7))));

        assertProblem(
                () -> MessageJson.readServer(bytes(json.replace("\"stat:stability\"", "\"stat:luck\""))),
                "card.modifiers[0].target",
                "unknown_value");
        assertProblem(
                () -> MessageJson.readServer(bytes(json.replace("\"economy\":2", "\"magic\":2"))),
                "card.development.magic",
                "unknown_value");
        assertProblem(
                () -> MessageJson.readServer(bytes(json.replace("\"tier\":\"partial\"", "\"tier\":\"meh\""))),
                "card.rolls[0].sectors[0].tier",
                "unknown_value");
        assertProblem(
                () -> MessageJson.readServer(
                        bytes(json.replace("\"holy_center\":391}", "\"holy_center\":391,\"x\":1}"))),
                "card.religion.x",
                "unknown_field");
        // Сектор, що випав, мусить бути серед секторів колеса.
        assertThat(catchProtocol(() -> MessageJson.readServer(
                                bytes(json.replace("\"result\":\"large\"", "\"result\":\"huge\""))))
                        .details())
                .containsEntry("location", "card.rolls[0]")
                .containsEntry("cause", "unknown_reference");
        assertThat(catchProtocol(() -> MessageJson.readServer(bytes(json.replace("\"id\":\"rel_3\"", "\"id\":\"3\""))))
                        .details())
                .containsEntry("location", "card.religion.id");
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
