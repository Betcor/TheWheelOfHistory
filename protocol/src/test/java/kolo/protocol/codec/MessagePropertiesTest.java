package kolo.protocol.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.engine.view.MapView;
import kolo.protocol.TestMessages;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.MapAssembler;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.Nicknames;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.PlayerToken;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.WorldInfo;
import kolo.protocol.message.YearPhase;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class MessagePropertiesTest {

    @Property(tries = 200)
    void anyMapSurvivesChunksAndJson(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 60) int cells,
            @ForAll @IntRange(min = 1, max = 70) int chunk) {
        MapView map = TestMessages.map(seed, cells);
        MapAssembler assembler = new MapAssembler();
        Optional<MapView> result = Optional.empty();

        for (ServerMessage message : MapChunks.split(map, chunk)) {
            ServerMessage read = MessageJson.readServer(MessageJson.write(message));
            assertThat(read).isEqualTo(message);
            switch (read) {
                case ServerMessage.MapStart start -> assembler.start(start);
                case ServerMessage.MapCells part -> result = assembler.add(part);
                default -> throw new AssertionError(read);
            }
        }

        assertThat(result).contains(map);
    }

    @Property(tries = 300)
    void anyErrorRoundTrips(@ForAll ErrorCode code, @ForAll("details") Map<String, Object> extra) {
        TreeMap<String, Object> details = new TreeMap<>(extra);
        code.requiredDetails().forEach(key -> details.put(key, "так"));
        ServerMessage.Error error = new ServerMessage.Error(code, details);

        assertThat(MessageJson.readServer(MessageJson.write(error))).isEqualTo(error);
    }

    @Property(tries = 300)
    void anyClientMessageRoundTrips(
            @ForAll long seed,
            @ForAll @LongRange(min = 1) long session,
            @ForAll @IntRange(min = 1) int player,
            @ForAll NpcShare share,
            @ForAll @IntRange(min = 1) int version,
            @ForAll("text") String hash,
            @ForAll("nickname") String nickname,
            @ForAll @IntRange(min = 0) int turn) {
        for (ClientMessage message : List.of(
                new ClientMessage.Hello(version, hash),
                new ClientMessage.ListLobbies(),
                new ClientMessage.CreateLobby(nickname, seed, share),
                new ClientMessage.JoinLobby(session, nickname),
                new ClientMessage.StartGame(),
                new ClientMessage.Rejoin(session, player, hash),
                new ClientMessage.Leave(),
                new ClientMessage.Ready(turn),
                new ClientMessage.ListWorlds(),
                new ClientMessage.LoadWorld(hash, nickname, Optional.empty()),
                new ClientMessage.LoadWorld(hash, nickname, Optional.of(new PlayerToken(player, hash))),
                new ClientMessage.AssignSeat(player, player),
                new ClientMessage.EndYear(turn),
                new ClientMessage.Resume(turn))) {
            assertThat(MessageJson.readClient(MessageJson.write(message))).isEqualTo(message);
        }
    }

    @Property(tries = 200)
    void anyLobbyMessageRoundTrips(
            @ForAll @LongRange(min = 1) long session,
            @ForAll long seed,
            @ForAll NpcShare share,
            @ForAll @IntRange(min = 0) int turn,
            @ForAll("players") List<PlayerInfo> players,
            @ForAll("text") String token,
            @ForAll @IntRange(min = 1, max = TurnTimer.MAX_SECONDS) int seconds) {
        TurnTimer timer = switch (seconds % 3) {
            case 0 -> TurnTimer.MANUAL;
            case 1 -> new TurnTimer(TurnTimer.Mode.LIVE, seconds);
            default -> new TurnTimer(TurnTimer.Mode.ASYNC, seconds);
        };
        ClientMessage set = new ClientMessage.SetTimer(timer);
        assertThat(MessageJson.readClient(MessageJson.write(set))).isEqualTo(set);
        LobbySetup setup = turn % 2 == 0
                ? new LobbySetup.NewWorld(seed, share, timer)
                : new LobbySetup.SavedWorld(token, seed, turn, timer);
        List<PlayerInfo> withHost = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            PlayerInfo p = players.get(i);
            withHost.add(new PlayerInfo(p.number(), p.nickname(), i == 0, p.connected(), p.ready(), p.country()));
        }
        for (ServerMessage message : List.of(
                new ServerMessage.Lobby(
                        session,
                        token,
                        setup,
                        withHost,
                        timer.timed() ? List.of(TurnTimer.MANUAL, timer) : List.of(TurnTimer.MANUAL)),
                new ServerMessage.Players(players),
                new ServerMessage.Joined(session, token, players.getFirst().number(), token),
                new ServerMessage.Lobbies(withHost.stream()
                        .map(p -> new LobbyInfo(p.number(), token, p.nickname(), withHost.size(), setup))
                        .toList()),
                new ServerMessage.Worlds(withHost.stream()
                        .map(p -> new WorldInfo(
                                p.nickname(),
                                p.ready() ? Optional.of(token) : Optional.empty(),
                                seed,
                                turn,
                                List.of(p.nickname(), token)))
                        .toList()))) {
            assertThat(MessageJson.readServer(MessageJson.write(message))).isEqualTo(message);
        }
    }

    @Property(tries = 200)
    void anyPhaseRoundTrips(
            @ForAll @IntRange(min = 0) int turn, @ForAll YearPhase phase, @ForAll @LongRange(min = 0) long left) {
        ServerMessage message = new ServerMessage.Phase(turn, phase);
        ServerMessage orders = new ServerMessage.Phase(turn, YearPhase.ORDERS, OptionalLong.of(left));

        assertThat(MessageJson.readServer(MessageJson.write(message))).isEqualTo(message);
        assertThat(MessageJson.readServer(MessageJson.write(orders))).isEqualTo(orders);
    }

    @Property(tries = 100)
    void anyCardRoundTrips(@ForAll @IntRange(min = 0, max = 1000) int number, @ForAll int seed) {
        ServerMessage message = new ServerMessage.OwnCountry(TestMessages.card(number, seed));

        assertThat(MessageJson.readServer(MessageJson.write(message))).isEqualTo(message);
    }

    @Provide
    Arbitrary<Map<String, Object>> details() {
        Arbitrary<Object> value = Arbitraries.oneOf(
                text().map(Object.class::cast),
                Arbitraries.longs().map(Object.class::cast),
                Arbitraries.integers().map(Object.class::cast),
                Arbitraries.of(true, false).map(Object.class::cast));
        return Arbitraries.maps(Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8), value)
                .ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<PlayerInfo>> players() {
        Arbitrary<PlayerInfo> player = Combinators.combine(
                        Arbitraries.integers().greaterOrEqual(1),
                        nickname(),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false),
                        Arbitraries.integers()
                                .between(-1, 39)
                                .map(n -> n < 0 ? OptionalInt.empty() : OptionalInt.of(n)))
                .as((number, nickname, connected, ready, country) ->
                        new PlayerInfo(number, nickname, false, connected, ready, country));
        return player.list().ofMinSize(1).ofMaxSize(16);
    }

    @Provide
    Arbitrary<String> nickname() {
        return Arbitraries.strings()
                .withCharRange('а', 'я')
                .withChars('\'', '"', 'Ї', '€', ' ', 'Z')
                .ofMinLength(1)
                .ofMaxLength(Nicknames.MAX_LENGTH)
                .filter(s -> s.strip().equals(s) && !s.isEmpty());
    }

    @Provide
    Arbitrary<String> text() {
        // Кирилиця, апострофи, лапки й керувальні символи мусять пройти JSON без змін.
        return Arbitraries.strings()
                .withCharRange('а', 'я')
                .withChars('\'', '"', '\\', '\n', '\t', 'Ї', '€', ' ')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.isBlank());
    }
}
