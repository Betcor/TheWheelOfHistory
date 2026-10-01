package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ContentException;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.Protocol;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.WorldInfo;
import kolo.protocol.message.YearPhase;
import kolo.server.TestServers;
import kolo.server.persistence.WorldDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Правила розмови сервера з клієнтом без мережі: рукостискання, створення світу, «Готово», помилки й закриття. */
@Timeout(60)
class ClientSessionTest {

    private static final String HASH = TestServers.CONTENT.hash();

    @TempDir
    Path worlds;

    private Sessions sessions;
    private final RecordingPeer peer = new RecordingPeer();
    private ClientSession session;

    @BeforeEach
    void start() {
        sessions = new Sessions(new WorldDirectory(worlds));
        session = new ClientSession(() -> TestServers.CONTENT, sessions, peer);
    }

    @AfterEach
    void close() {
        sessions.closeAll(30, TimeUnit.SECONDS);
    }

    @Test
    void helloIsWelcomed() throws Exception {
        session.handle(Handshake.hello(HASH));

        assertThat(peer.next()).isEqualTo(new ServerMessage.Welcome(Protocol.VERSION, HASH));
        assertThat(peer.closed()).isFalse();
        assertThat(session.welcomed()).isTrue();
    }

    @Test
    void otherProtocolVersionIsRejectedAndClosed() throws Exception {
        session.handle(new ClientMessage.Hello(Protocol.VERSION + 1, HASH));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.VERSION_MISMATCH);
        assertThat(error.details()).containsEntry("part", Handshake.PROTOCOL);
        assertThat(peer.closed()).isTrue();
        assertThat(session.welcomed()).isFalse();
    }

    @Test
    void otherContentIsRejectedAndClosed() throws Exception {
        session.handle(Handshake.hello("b".repeat(64)));

        assertThat(peer.error().details()).containsEntry("part", Handshake.CONTENT);
        assertThat(peer.closed()).isTrue();
    }

    @Test
    void requestBeforeHelloClosesTheConnection() throws Exception {
        session.handle(new ClientMessage.CreateLobby("Оля", 1, NpcShare.FEW));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.PROTOCOL_ERROR);
        assertThat(error.details()).containsEntry("problem", "hello_expected");
        assertThat(peer.closed()).isTrue();
        assertThat(sessions.active()).isEmpty();
    }

    @Test
    void repeatedHelloClosesTheConnection() throws Exception {
        welcome();

        session.handle(Handshake.hello(HASH));

        assertThat(peer.error().details()).containsEntry("problem", "hello_repeated");
        assertThat(peer.closed()).isTrue();
    }

    @Test
    void createdLobbyIsListedAndStartsTheGame() throws Exception {
        welcome();

        session.handle(new ClientMessage.CreateLobby("Оля", 42, NpcShare.NORMAL));

        ServerMessage.Joined joined = peer.joined();
        assertThat(peer.lobby().session()).isEqualTo(joined.session());
        session.handle(new ClientMessage.ListLobbies());
        assertThat(peer.next())
                .isEqualTo(new ServerMessage.Lobbies(List.of(new LobbyInfo(
                        joined.session(),
                        joined.world(),
                        "Оля",
                        1,
                        new LobbySetup.NewWorld(42, NpcShare.NORMAL, TurnTimer.MANUAL)))));

        session.handle(new ClientMessage.StartGame());

        assertThat(peer.map()).isEqualTo(TestServers.map(42, 1, NpcShare.NORMAL));
        peer.expectOrders(0);
        assertThat(session.session()).isPresent();
        assertThat(peer.closed()).isFalse();
        // Почату гру вже не видно у списку лобі.
        session.handle(new ClientMessage.ListLobbies());
        assertThat(peer.next()).isEqualTo(new ServerMessage.Lobbies(List.of()));
    }

    @Test
    void anotherClientJoinsTheLobbyAndPlays() throws Exception {
        welcome();
        session.handle(new ClientMessage.CreateLobby("Оля", 3, NpcShare.FEW));
        long id = peer.joined().session();
        peer.lobby();
        RecordingPeer otherPeer = new RecordingPeer();
        ClientSession other = welcomed(otherPeer);

        other.handle(new ClientMessage.JoinLobby(id, "Ігор"));

        assertThat(otherPeer.joined().player()).isEqualTo(2);
        assertThat(otherPeer.lobby().players()).hasSize(2);
        assertThat(peer.lobby().players()).hasSize(2);
        session.handle(new ClientMessage.StartGame());
        peer.map();
        peer.expectOrders(0);
        otherPeer.map();
        otherPeer.expectOrders(0);
        session.handle(new ClientMessage.Ready(0));
        peer.players();
        other.handle(new ClientMessage.Ready(0));
        otherPeer.players();
        otherPeer.expectYear(0);
        peer.expectYear(0);
    }

    @Test
    void joiningAnUnknownSessionIsNotFound() throws Exception {
        welcome();

        session.handle(new ClientMessage.JoinLobby(99, "Оля"));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(error.details()).containsEntry("what", "session").containsEntry("id", 99L);
        assertThat(peer.closed()).isFalse();
    }

    @Test
    void readyWithoutSessionIsAnErrorButTheConnectionStaysOpen() throws Exception {
        welcome();

        session.handle(new ClientMessage.Ready(0));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        assertThat(error.details()).containsEntry("turn", 0L);
        assertThat(peer.closed()).isFalse();
    }

    @Test
    void startWithoutSessionIsForbidden() throws Exception {
        welcome();

        session.handle(new ClientMessage.StartGame());

        assertThat(peer.error().code()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(peer.closed()).isFalse();
    }

    @Test
    void timerEndYearAndResumeWithoutSessionAreErrors() throws Exception {
        welcome();

        session.handle(new ClientMessage.SetTimer(TurnTimer.MANUAL));
        assertThat(peer.error().code()).isEqualTo(ErrorCode.FORBIDDEN);
        session.handle(new ClientMessage.EndYear(0));
        assertThat(peer.error().code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        session.handle(new ClientMessage.Resume(0));
        assertThat(peer.error().code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        assertThat(peer.closed()).isFalse();
    }

    @Test
    void hostSetsTheTimerAndEndsTheYear() throws Exception {
        welcome();
        TurnTimer live = new TurnTimer(TurnTimer.Mode.LIVE, 180);
        session.handle(new ClientMessage.CreateLobby("Оля", 42, NpcShare.FEW));
        peer.joined();
        peer.lobby();

        session.handle(new ClientMessage.SetTimer(live));

        assertThat(peer.lobby().setup().timer()).isEqualTo(live);
        session.handle(new ClientMessage.StartGame());
        peer.map();
        peer.players();
        peer.expectPhase(0, YearPhase.START_OF_YEAR);
        assertThat(peer.phase().timeLeftMillis()).isPresent();
        // Сесія не на паузі: відновлювати нічого — відповідає вже актор сесії.
        session.handle(new ClientMessage.Resume(0));
        assertThat(peer.error().code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        session.handle(new ClientMessage.EndYear(0));
        peer.expectPhase(0, YearPhase.RESOLVING);
    }

    @Test
    void newLobbyLeavesThePreviousSession() throws Exception {
        welcome();
        soloGame(1);
        SessionActor first = session.session().orElseThrow();

        session.handle(new ClientMessage.CreateLobby("Оля", 2, NpcShare.FEW));
        peer.joined();
        peer.lobby();

        assertThat(first.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(first.state()).isEqualTo(SessionState.CLOSED);
        assertThat(sessions.active()).containsExactly(session.session().orElseThrow());
    }

    @Test
    void leftSessionIsSilent() throws Exception {
        welcome();
        session.handle(new ClientMessage.CreateLobby("Оля", 3, NpcShare.FEW));
        long id = peer.joined().session();
        peer.lobby();
        RecordingPeer otherPeer = new RecordingPeer();
        ClientSession other = welcomed(otherPeer);
        other.handle(new ClientMessage.JoinLobby(id, "Ігор"));
        otherPeer.joined();
        otherPeer.lobby();
        peer.lobby();

        other.handle(new ClientMessage.Leave());

        // Хост бачить вихід, а той, хто вийшов, — уже нічого з цієї сесії.
        assertThat(peer.lobby().players()).hasSize(1);
        session.handle(new ClientMessage.StartGame());
        peer.map();
        assertThat(otherPeer.quiet()).isTrue();
        assertThat(other.session()).isEmpty();
    }

    @Test
    void disconnectedPlayerRejoinsWithTheToken() throws Exception {
        welcome();
        session.handle(new ClientMessage.CreateLobby("Оля", 3, NpcShare.FEW));
        long id = peer.joined().session();
        peer.lobby();
        RecordingPeer otherPeer = new RecordingPeer();
        ClientSession other = welcomed(otherPeer);
        other.handle(new ClientMessage.JoinLobby(id, "Ігор"));
        ServerMessage.Joined joined = otherPeer.joined();
        otherPeer.lobby();
        peer.lobby();
        session.handle(new ClientMessage.StartGame());
        peer.map();
        peer.expectOrders(0);
        other.disconnected();
        peer.players();
        RecordingPeer backPeer = new RecordingPeer();
        ClientSession back = welcomed(backPeer);

        back.handle(new ClientMessage.Rejoin(id, joined.player(), joined.token()));

        assertThat(backPeer.joined()).isEqualTo(joined);
        assertThat(backPeer.map()).isEqualTo(TestServers.map(3, 2, NpcShare.FEW));
        assertThat(back.session()).isPresent();
    }

    @Test
    void disconnectLeavesTheSession() throws Exception {
        welcome();
        soloGame(1);
        SessionActor actor = session.session().orElseThrow();

        session.disconnected();

        assertThat(actor.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(session.session()).isEmpty();
        assertThat(sessions.active()).isEmpty();
    }

    @Test
    void invalidServerContentIsReportedAndClosed() throws Exception {
        Supplier<ContentPack> broken = () -> {
            throw new ContentException(ErrorCode.INVALID_CONTENT, Map.of("file", "map.yaml"));
        };

        new ClientSession(broken, sessions, peer).handle(Handshake.hello(HASH));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.INVALID_CONTENT);
        assertThat(error.details()).containsEntry("file", "map.yaml");
        assertThat(peer.closed()).isTrue();
    }

    @Test
    void serverBugsAreNotHiddenAsErrors() {
        Supplier<ContentPack> buggy = () -> {
            throw new IllegalStateException("баг");
        };

        assertThatThrownBy(() -> new ClientSession(buggy, sessions, peer).handle(Handshake.hello(HASH)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void savedWorldsAreListedAndATrustedClientLoadsOneAsAGuest() throws Exception {
        welcome();
        soloGame(42);
        session.handle(new ClientMessage.Leave());
        awaitNoSessions();
        RecordingPeer local = new RecordingPeer();
        ClientSession trusted = new ClientSession(() -> TestServers.CONTENT, sessions, local, true);
        trusted.handle(Handshake.hello(HASH));
        local.next();

        trusted.handle(new ClientMessage.ListWorlds());

        List<WorldInfo> worlds = local.next(ServerMessage.Worlds.class).worlds();
        assertThat(worlds).hasSize(1);
        WorldInfo world = worlds.getFirst();
        assertThat(world.name()).isEqualTo("world-42");
        assertThat(world.seed()).isEqualTo(42);
        assertThat(world.turn()).isZero();
        assertThat(world.players()).containsExactly("Оля");
        assertThat(world.key()).isPresent();

        trusted.handle(new ClientMessage.LoadWorld("world-42", "Марко", Optional.empty()));

        assertThat(local.joined().player()).isEqualTo(2);
        assertThat(local.lobby().setup()).isEqualTo(new LobbySetup.SavedWorld("world-42", 42, 0, TurnTimer.MANUAL));
        trusted.handle(new ClientMessage.AssignSeat(2, 1));
        ServerMessage.Joined seated = local.joined();
        assertThat(seated.player()).isEqualTo(1);
        assertThat(seated.world()).isEqualTo(world.key().orElseThrow());
        // Відкритого світу вже немає в списку.
        trusted.handle(new ClientMessage.ListWorlds());
        assertThat(local.next()).isInstanceOf(ServerMessage.Lobby.class);
        assertThat(local.next(ServerMessage.Worlds.class).worlds()).isEmpty();
    }

    @Test
    void clientFromTheNetworkNeedsASeatTokenToLoad() throws Exception {
        welcome();
        session.handle(new ClientMessage.AssignSeat(1, 1));
        assertThat(peer.error().code()).isEqualTo(ErrorCode.FORBIDDEN);
        soloGame(43);
        session.handle(new ClientMessage.Leave());
        awaitNoSessions();

        session.handle(new ClientMessage.LoadWorld("world-43", "Марко", Optional.empty()));

        assertThat(peer.error().code()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(peer.closed()).isFalse();
    }

    private void awaitNoSessions() throws InterruptedException {
        for (SessionActor open : sessions.active()) {
            assertThat(open.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void welcome() throws InterruptedException {
        session.handle(Handshake.hello(HASH));
        assertThat(peer.next()).isInstanceOf(ServerMessage.Welcome.class);
    }

    private ClientSession welcomed(RecordingPeer other) throws InterruptedException {
        ClientSession client = new ClientSession(() -> TestServers.CONTENT, sessions, other);
        client.handle(Handshake.hello(HASH));
        assertThat(other.next()).isInstanceOf(ServerMessage.Welcome.class);
        return client;
    }

    /** Лобі хоста й одразу гра; чекає наказів року 0. */
    private void soloGame(long seed) throws InterruptedException {
        session.handle(new ClientMessage.CreateLobby("Оля", seed, NpcShare.FEW));
        peer.joined();
        peer.lobby();
        session.handle(new ClientMessage.StartGame());
        peer.map();
        peer.expectOrders(0);
    }
}
