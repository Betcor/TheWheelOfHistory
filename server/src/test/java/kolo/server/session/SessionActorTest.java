package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.engine.state.WorldLimits;
import kolo.engine.state.WorldState;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import kolo.server.TestServers;
import kolo.server.auth.PlayerTokens;
import kolo.server.persistence.PlayerRecord;
import kolo.server.persistence.SavedPlayer;
import kolo.server.persistence.SavedTurn;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Стан-машина сесії без мережі: лобі, старт, фази року, «Готово», повернення, запис року, пауза й закриття. */
@Timeout(60)
class SessionActorTest {

    @TempDir
    Path worlds;

    private static final LobbySetup FEW_11 = new LobbySetup.NewWorld(11, NpcShare.FEW, TurnTimer.MANUAL);

    private final RecordingPeer host = new RecordingPeer();
    private final AtomicInteger closed = new AtomicInteger();
    private final ManualClock clock = new ManualClock();
    /** Ключ світу з лобі хоста. */
    private String worldKey;

    // ---- Лобі ----

    @Test
    void newSessionIsInTheLobby() {
        SessionActor session = session(SessionActor.engine(TestServers.CONTENT));

        assertThat(session.state()).isEqualTo(SessionState.LOBBY);
        assertThat(session.id()).isEqualTo(7);
        assertThat(session.lobby()).isEmpty();
        session.close();
    }

    @Test
    void openedLobbyGivesTheHostATokenAndIsListed() throws Exception {
        SessionActor session = session(SessionActor.engine(TestServers.CONTENT));

        session.open(host, "Оля", 11, NpcShare.FEW);

        ServerMessage.Joined joined = host.joined();
        assertThat(joined.session()).isEqualTo(7);
        assertThat(joined.player()).isEqualTo(1);
        assertThat(joined.token()).hasSize(64);
        assertThat(joined.world()).hasSize(32);
        assertThat(host.lobby())
                .isEqualTo(new ServerMessage.Lobby(
                        7,
                        joined.world(),
                        FEW_11,
                        List.of(new PlayerInfo(1, "Оля", true, true, false, OptionalInt.empty())),
                        TestServers.TIMERS));
        assertThat(session.lobby()).contains(new LobbyInfo(7, joined.world(), "Оля", 1, FEW_11));
        session.close();
    }

    @Test
    void joiningPlayersSeeEachOther() throws Exception {
        SessionActor session = lobby();
        RecordingPeer guest = new RecordingPeer();

        session.join(guest, "Ігор");

        ServerMessage.Joined joined = guest.joined();
        assertThat(joined.player()).isEqualTo(2);
        assertThat(joined.world()).isEqualTo(worldKey);
        List<PlayerInfo> players = List.of(
                new PlayerInfo(1, "Оля", true, true, false, OptionalInt.empty()),
                new PlayerInfo(2, "Ігор", false, true, false, OptionalInt.empty()));
        assertThat(guest.lobby().players()).isEqualTo(players);
        assertThat(host.lobby().players()).isEqualTo(players);
        assertThat(session.lobby()).contains(new LobbyInfo(7, worldKey, "Оля", 2, FEW_11));
        session.close();
    }

    @Test
    void takenNicknameIsRejectedIgnoringCase() throws Exception {
        SessionActor session = lobby();
        RecordingPeer guest = new RecordingPeer();

        session.join(guest, "оЛЯ");

        ServerMessage.Error error = guest.error();
        assertThat(error.code()).isEqualTo(ErrorCode.NICKNAME_TAKEN);
        assertThat(error.details()).containsEntry("nickname", "оЛЯ");
        assertThat(host.quiet()).isTrue();
        session.close();
    }

    @Test
    void fullLobbyIsRejected() throws Exception {
        SessionActor session = lobby();
        for (int n = 2; n <= WorldLimits.MAX_PLAYERS; n++) {
            RecordingPeer guest = new RecordingPeer();
            session.join(guest, "Гравець " + n);
            guest.joined();
        }
        RecordingPeer late = new RecordingPeer();

        session.join(late, "Запізнився");

        ServerMessage.Error error = late.error();
        assertThat(error.code()).isEqualTo(ErrorCode.LOBBY_FULL);
        assertThat(error.details()).containsEntry("max", (long) WorldLimits.MAX_PLAYERS);
        session.close();
    }

    @Test
    void hostLeavingTheLobbyHandsItToTheNextPlayer() throws Exception {
        SessionActor session = lobby();
        RecordingPeer guest = new RecordingPeer();
        session.join(guest, "Ігор");
        guest.joined();
        guest.lobby();
        host.lobby();

        session.leave(host);

        assertThat(guest.lobby().players())
                .containsExactly(new PlayerInfo(2, "Ігор", true, true, false, OptionalInt.empty()));
        assertThat(session.lobby()).contains(new LobbyInfo(7, worldKey, "Ігор", 1, FEW_11));
        // Тепер почати гру може новий хост.
        session.start(guest);
        guest.map();
        guest.expectOrders(0);
        session.close();
    }

    @Test
    void lastPlayerLeavingTheLobbyClosesIt() throws Exception {
        SessionActor session = lobby();

        leaveAndAwait(session);

        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
        assertThat(session.lobby()).isEmpty();
        try (Stream<Path> files = Files.list(worlds)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void onlyTheHostStartsTheGame() throws Exception {
        SessionActor session = lobby();
        RecordingPeer guest = new RecordingPeer();
        RecordingPeer stranger = new RecordingPeer();
        session.join(guest, "Ігор");
        guest.joined();
        guest.lobby();

        session.start(guest);
        session.start(stranger);

        assertThat(guest.error().code()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(stranger.error().code()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(session.state()).isEqualTo(SessionState.LOBBY);
        session.close();
    }

    // ---- Гра ----

    @Test
    void startedWorldIsSavedWithItsPlayers() throws Exception {
        SessionActor session = lobby();
        RecordingPeer guest = new RecordingPeer();
        session.join(guest, "Ігор");
        String guestToken = guest.joined().token();
        guest.lobby();
        host.lobby();

        session.start(host);

        assertThat(host.map()).isEqualTo(TestServers.map(11, 2, NpcShare.FEW));
        assertThat(guest.map()).isEqualTo(TestServers.map(11, 2, NpcShare.FEW));
        assertThat(host.players().players())
                .containsExactly(
                        new PlayerInfo(1, "Оля", true, true, false, OptionalInt.of(0)),
                        new PlayerInfo(2, "Ігор", false, true, false, OptionalInt.of(1)));
        host.expectPhase(0, YearPhase.START_OF_YEAR);
        host.expectPhase(0, YearPhase.ORDERS);
        guest.expectOrders(0);
        assertThat(session.state()).isEqualTo(SessionState.RUNNING);
        assertThat(session.lobby()).isEmpty();
        session.close();
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();

        try (WorldStore store = WorldStore.open(worlds.resolve("world-11" + WorldStore.EXTENSION))) {
            assertThat(store.meta().name()).isEqualTo("world-11");
            // Ключ, названий гравцям у лобі, — ключ файлу.
            assertThat(store.meta().key()).isEqualTo(worldKey);
            assertThat(store.lastTurn()).isZero();
            List<PlayerRecord> players =
                    store.players().stream().map(SavedPlayer::player).toList();
            assertThat(players)
                    .extracting(PlayerRecord::number, PlayerRecord::nickname, PlayerRecord::country, PlayerRecord::host)
                    .containsExactly(tuple(1, "Оля", 0, true), tuple(2, "Ігор", 1, false));
            // У файлі лише хеш токена.
            assertThat(players.get(1).tokenHash()).isEqualTo(PlayerTokens.hash(guestToken));
        }
    }

    @Test
    void joiningAStartedGameIsRejected() throws Exception {
        SessionActor session = running(SessionActor.engine(TestServers.CONTENT));
        RecordingPeer late = new RecordingPeer();

        session.join(late, "Ігор");
        session.start(host);

        assertThat(late.error().code()).isEqualTo(ErrorCode.LOBBY_CLOSED);
        assertThat(host.error().code()).isEqualTo(ErrorCode.LOBBY_CLOSED);
        session.close();
    }

    @Test
    void readyResolvesAndSavesTheYear() throws Exception {
        SessionActor session = running(SessionActor.engine(TestServers.CONTENT));

        for (int turn = 0; turn < 3; turn++) {
            session.ready(host, turn);
            host.expectYear(turn);
        }
        leaveAndAwait(session);

        try (WorldStore store = WorldStore.open(worlds.resolve("world-11" + WorldStore.EXTENSION))) {
            assertThat(store.turns()).extracting(SavedTurn::turn).containsExactly(0, 1, 2, 3);
            WorldState latest = store.loadLatest().state();
            assertThat(latest.turn()).isEqualTo(3);
            WorldState initial = store.loadState(0).orElseThrow().state();
            initial.setTurn(3);
            // Порожні роки змінюють лише номер року.
            assertThat(latest).isEqualTo(initial);
        }
    }

    @Test
    void yearWaitsForEveryConnectedPlayer() throws Exception {
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = runningWith(guest);

        session.ready(host, 0);

        assertThat(host.players().players()).extracting(PlayerInfo::ready).containsExactly(true, false);
        assertThat(guest.players().players()).extracting(PlayerInfo::ready).containsExactly(true, false);
        // Повторне «Готово» нічого не змінює.
        session.ready(host, 0);
        assertThat(host.quiet()).isTrue();

        session.ready(guest, 0);

        host.expectYear(0);
        guest.expectYear(0);
        session.close();
    }

    @Test
    void playerWhoLeftIsNotWaitedFor() throws Exception {
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = runningWith(guest);
        session.ready(host, 0);
        host.players();
        guest.players();

        session.leave(guest);

        host.expectPhase(0, YearPhase.RESOLVING);
        host.expectPhase(0, YearPhase.REPORT);
        // Гравець, що пішов, лишається у світі — не на зв'язку.
        assertThat(host.players().players())
                .containsExactly(
                        new PlayerInfo(1, "Оля", true, true, false, OptionalInt.of(0)),
                        new PlayerInfo(2, "Ігор", false, false, false, OptionalInt.of(1)));
        host.expectPhase(1, YearPhase.START_OF_YEAR);
        host.expectPhase(1, YearPhase.ORDERS);
        assertThat(guest.quiet()).isTrue();
        session.close();
    }

    @Test
    void playerRejoinsWithTheTokenAndGetsTheWorld() throws Exception {
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = lobby();
        session.join(guest, "Ігор");
        ServerMessage.Joined joined = guest.joined();
        startTwo(session, guest);
        session.leave(guest);
        host.players();
        RecordingPeer back = new RecordingPeer();

        session.rejoin(back, joined.player(), joined.token());

        assertThat(back.joined()).isEqualTo(joined);
        assertThat(back.map()).isEqualTo(TestServers.map(11, 2, NpcShare.FEW));
        back.expectPhase(0, YearPhase.ORDERS);
        PlayerInfo returned = new PlayerInfo(2, "Ігор", false, true, false, OptionalInt.of(1));
        assertThat(back.players().players()).contains(returned);
        assertThat(host.players().players()).contains(returned);
        // Повернувся — і рік знову чекає на нього.
        session.ready(host, 0);
        host.players();
        session.ready(back, 0);
        back.players();
        back.expectYear(0);
        session.close();
    }

    @Test
    void rejoinFromANewConnectionClosesTheOldOne() throws Exception {
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = lobby();
        session.join(guest, "Ігор");
        ServerMessage.Joined joined = guest.joined();
        startTwo(session, guest);
        RecordingPeer back = new RecordingPeer();

        session.rejoin(back, joined.player(), joined.token());

        back.joined();
        back.map();
        assertThat(guest.closed()).isTrue();
        // Старе з'єднання вже не гравець: його вихід нічого не змінює.
        session.leave(guest);
        back.expectPhase(0, YearPhase.ORDERS);
        assertThat(back.players().players()).extracting(PlayerInfo::connected).containsExactly(true, true);
        assertThat(back.quiet()).isTrue();
        session.close();
    }

    @Test
    void wrongTokenIsUnauthorized() throws Exception {
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = lobby();
        session.join(guest, "Ігор");
        ServerMessage.Joined joined = guest.joined();
        startTwo(session, guest);
        RecordingPeer impostor = new RecordingPeer();

        session.rejoin(impostor, joined.player(), "0".repeat(64));
        session.rejoin(impostor, 9, joined.token());

        assertThat(impostor.error().code()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(impostor.error().details()).containsEntry("player", 9L);
        assertThat(guest.closed()).isFalse();
        session.close();
    }

    @Test
    void readyForAnotherYearIsRejected() throws Exception {
        SessionActor session = running(SessionActor.engine(TestServers.CONTENT));

        session.ready(host, 1);

        ServerMessage.Error error = host.error();
        assertThat(error.code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        assertThat(error.details()).containsEntry("turn", 1L);
        assertThat(host.closed()).isFalse();
        // Сесія живе далі: правильний рік приймається.
        session.ready(host, 0);
        host.expectYear(0);
        session.close();
    }

    @Test
    void repeatedReadyForAResolvedYearIsRejected() throws Exception {
        SessionActor session = running(SessionActor.engine(TestServers.CONTENT));
        session.ready(host, 0);
        host.expectYear(0);

        session.ready(host, 0);

        assertThat(host.error().code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        session.close();
    }

    @Test
    void readyQueuedDuringGenerationAppliesToTheFirstYear() throws Exception {
        SessionActor session = lobby();
        session.start(host);
        // Завдання сесії виконуються по черзі: «Готово» дійде після генерації — уже в році 0.
        session.ready(host, 0);

        host.map();
        host.expectOrders(0);
        host.expectYear(0);
        session.close();
    }

    @Test
    void strangersReadyIsRejectedAndLeaveIgnored() throws Exception {
        SessionActor session = running(SessionActor.engine(TestServers.CONTENT));
        RecordingPeer stranger = new RecordingPeer();

        session.ready(stranger, 0);
        session.leave(stranger);

        assertThat(stranger.error().code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        assertThat(host.quiet()).isTrue();
        assertThat(session.state()).isEqualTo(SessionState.RUNNING);
        session.close();
    }

    @Test
    void failedYearPausesTheSessionAndKeepsTheFile() throws Exception {
        UnaryOperator<WorldState> broken = state -> {
            throw new InvariantViolationException(ErrorDetails.of("check", "test"));
        };
        SessionActor session = running(broken);

        session.ready(host, 0);

        host.expectPhase(0, YearPhase.RESOLVING);
        ServerMessage.Error error = host.error();
        assertThat(error.code()).isEqualTo(ErrorCode.INVARIANT_VIOLATION);
        assertThat(error.details()).containsEntry("check", "test");
        assertThat(session.state()).isEqualTo(SessionState.PAUSED);
        // На паузі роки не йдуть.
        session.ready(host, 0);
        assertThat(host.error().code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        leaveAndAwait(session);
        try (WorldStore store = WorldStore.open(worlds.resolve("world-11" + WorldStore.EXTENSION))) {
            assertThat(store.lastTurn()).isZero();
        }
    }

    @Test
    void rejoinDuringPauseRepeatsThePauseError() throws Exception {
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = new SessionActor(
                7,
                TestServers.CONTENT,
                new WorldDirectory(worlds),
                new PlayerTokens(),
                state -> {
                    throw new InvariantViolationException(ErrorDetails.of("check", "test"));
                },
                s -> closed.incrementAndGet(),
                clock);
        session.open(host, "Оля", 11, NpcShare.FEW);
        host.joined();
        host.lobby();
        session.join(guest, "Ігор");
        ServerMessage.Joined joined = guest.joined();
        startTwo(session, guest);
        session.ready(host, 0);
        host.players();
        session.ready(guest, 0);
        guest.players();
        guest.expectPhase(0, YearPhase.RESOLVING);
        guest.error();
        RecordingPeer back = new RecordingPeer();

        session.rejoin(back, joined.player(), joined.token());

        back.joined();
        back.map();
        back.expectPhase(0, YearPhase.RESOLVING);
        assertThat(back.error().code()).isEqualTo(ErrorCode.INVARIANT_VIOLATION);
        session.close();
    }

    @Test
    void unwritableDirectoryIsReportedAndClosesTheSession() throws Exception {
        Path notADirectory = Files.writeString(worlds.resolve("file"), "x");
        SessionActor session = new SessionActor(
                1,
                TestServers.CONTENT,
                new WorldDirectory(notADirectory),
                new PlayerTokens(),
                SessionActor.engine(TestServers.CONTENT),
                s -> closed.incrementAndGet(),
                clock);
        session.open(host, "Оля", 1, NpcShare.FEW);
        host.joined();
        host.lobby();

        session.start(host);

        ServerMessage.Error error = host.error();
        assertThat(error.code()).isEqualTo(ErrorCode.SAVE_FILE_ERROR);
        assertThat(error.details()).containsEntry("operation", "create_directory");
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(closed).hasValue(1);
        assertThat(host.closed()).isFalse();
    }

    @Test
    void lastPlayerLeavingClosesTheSession() throws Exception {
        SessionActor session = running(SessionActor.engine(TestServers.CONTENT));

        leaveAndAwait(session);

        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
        assertThat(closed).hasValue(1);
        // Закрита сесія мовчить.
        session.ready(host, 0);
        assertThat(host.quiet()).isTrue();
    }

    @Test
    void serverBugClosesTheSessionAndItsConnections() throws Exception {
        UnaryOperator<WorldState> buggy = state -> {
            throw new IllegalStateException("баг сервера");
        };
        SessionActor session = running(buggy);

        session.ready(host, 0);

        host.expectPhase(0, YearPhase.RESOLVING);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(host.closed()).isTrue();
        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
    }

    @Test
    void sessionRunsInItsOwnThread() throws Exception {
        List<String> threads = new CopyOnWriteArrayList<>();
        SessionActor session = running(state -> {
            threads.add(Thread.currentThread().getName());
            return SessionActor.engine(TestServers.CONTENT).apply(state);
        });

        session.ready(host, 0);
        host.expectYear(0);

        assertThat(threads).containsExactly("kolo-session-7");
        session.close();
    }

    // ---- Допоміжне ----

    private SessionActor session(UnaryOperator<WorldState> years) {
        return new SessionActor(
                7,
                TestServers.CONTENT,
                new WorldDirectory(worlds),
                new PlayerTokens(),
                years,
                s -> closed.incrementAndGet(),
                clock);
    }

    /** Лобі з хостом «Оля» і малим світом. */
    private SessionActor lobby() throws InterruptedException {
        SessionActor session = session(SessionActor.engine(TestServers.CONTENT));
        session.open(host, "Оля", 11, NpcShare.FEW);
        worldKey = host.joined().world();
        host.lobby();
        return session;
    }

    /** Одиночна сесія з малим світом, що чекає наказів року 0. */
    private SessionActor running(UnaryOperator<WorldState> years) throws InterruptedException {
        SessionActor session = session(years);
        session.open(host, "Оля", 11, NpcShare.FEW);
        host.joined();
        host.lobby();
        session.start(host);
        assertThat(host.map()).isEqualTo(TestServers.map(11, 1, NpcShare.FEW));
        host.expectOrders(0);
        return session;
    }

    /** Сесія хоста й гостя «Ігор», що чекає наказів року 0. */
    private SessionActor runningWith(RecordingPeer guest) throws InterruptedException {
        SessionActor session = lobby();
        session.join(guest, "Ігор");
        guest.joined();
        startTwo(session, guest);
        return session;
    }

    /** Гість уже отримав свій номер; хост починає гру, обидва доходять до наказів року 0. */
    private void startTwo(SessionActor session, RecordingPeer guest) throws InterruptedException {
        guest.lobby();
        host.lobby();
        session.start(host);
        for (RecordingPeer peer : new ArrayList<>(List.of(host, guest))) {
            peer.map();
            peer.expectOrders(0);
        }
    }

    private void leaveAndAwait(SessionActor session) throws InterruptedException {
        session.leave(host);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
    }
}
