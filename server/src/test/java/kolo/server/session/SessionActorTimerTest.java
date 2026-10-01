package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.message.PlayerToken;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import kolo.server.TestServers;
import kolo.server.auth.PlayerTokens;
import kolo.server.persistence.SavedPlayer;
import kolo.server.persistence.SavedTimer;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Таймер ходу (GD §6.1) і автопілот (GD §6.3): вибір у лобі, межа фази наказів, «Завершити рік» хоста. */
@Timeout(60)
class SessionActorTimerTest {

    private static final TurnTimer LIVE_2 = new TurnTimer(TurnTimer.Mode.LIVE, 120);
    private static final Duration TWO_MINUTES = Duration.ofSeconds(120);

    @TempDir
    Path dir;

    private final ManualClock clock = new ManualClock();
    private final RecordingPeer host = new RecordingPeer();
    private final RecordingPeer guest = new RecordingPeer();
    private WorldDirectory worlds;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        worlds = new WorldDirectory(dir);
    }

    @Test
    void hostChoosesATimerFromTheContent() throws Exception {
        SessionActor session = lobbyWithGuest();

        session.setTimer(host, LIVE_2);

        for (RecordingPeer peer : List.of(host, guest)) {
            ServerMessage.Lobby lobby = peer.lobby();
            assertThat(lobby.setup().timer()).isEqualTo(LIVE_2);
            assertThat(lobby.timers()).isEqualTo(TestServers.TIMERS);
        }
        assertThat(session.lobby().orElseThrow().setup().timer()).isEqualTo(LIVE_2);
        // Той самий таймер ще раз — без змін і без повідомлень.
        session.setTimer(host, LIVE_2);
        session.setTimer(guest, TurnTimer.MANUAL);
        assertThat(guest.error().code()).isEqualTo(ErrorCode.FORBIDDEN);
        session.setTimer(host, new TurnTimer(TurnTimer.Mode.LIVE, 61));
        ServerMessage.Error error = host.error();
        assertThat(error.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(error.details()).containsEntry("field", "timer");
        assertThat(host.quiet()).isTrue();
        session.close();
    }

    @Test
    void timerCannotChangeOnceTheGameRuns() throws Exception {
        SessionActor session = running(TurnTimer.MANUAL);

        session.setTimer(host, LIVE_2);

        assertThat(host.error().code()).isEqualTo(ErrorCode.LOBBY_CLOSED);
        session.close();
    }

    @Test
    void manualYearHasNoDeadline() throws Exception {
        SessionActor session = running(TurnTimer.MANUAL);

        clock.advance(Duration.ofDays(30));

        assertThat(clock.pending()).isZero();
        assertThat(host.quiet()).isTrue();
        session.close();
    }

    @Test
    void yearResolvesWhenTimeRunsOutAndCountsMissedPlayers() throws Exception {
        SessionActor session = running(LIVE_2);
        session.ready(host, 0);
        host.players();
        guest.players();

        clock.advance(TWO_MINUTES.minusSeconds(1));
        assertThat(host.quiet()).isTrue();
        clock.advance(Duration.ofSeconds(1));

        for (RecordingPeer peer : List.of(host, guest)) {
            peer.expectPhase(0, YearPhase.RESOLVING);
            peer.expectPhase(0, YearPhase.REPORT);
            peer.players();
            peer.expectPhase(1, YearPhase.START_OF_YEAR);
            assertThat(peer.phase()).isEqualTo(orders(1, TWO_MINUTES));
        }
        // Гість не натиснув «Готово» — за нього діяв автопілот.
        assertThat(missedTurnsAfterClosing(session)).containsExactly(0, 1);
    }

    @Test
    void readyPlayersResolveTheYearBeforeTheDeadline() throws Exception {
        SessionActor session = running(LIVE_2);
        clock.advance(Duration.ofSeconds(30));

        session.ready(host, 0);
        host.players();
        session.ready(guest, 0);

        host.expectPhase(0, YearPhase.RESOLVING);
        host.expectPhase(0, YearPhase.REPORT);
        host.players();
        host.expectPhase(1, YearPhase.START_OF_YEAR);
        assertThat(host.phase()).isEqualTo(orders(1, TWO_MINUTES));
        // Межа року 0 скасована: лишилася лише межа року 1.
        assertThat(clock.pending()).isEqualTo(1);
        clock.advance(TWO_MINUTES.minusSeconds(30));
        assertThat(host.quiet()).isTrue();
        assertThat(missedTurnsAfterClosing(session)).containsExactly(0, 0);
    }

    @Test
    void hostEndsTheYearWithoutWaiting() throws Exception {
        SessionActor session = running(TurnTimer.MANUAL);

        session.endYear(guest, 0);
        assertThat(guest.error().code()).isEqualTo(ErrorCode.FORBIDDEN);
        session.endYear(host, 1);
        assertThat(host.error().code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        session.endYear(host, 0);

        host.expectYear(0);
        guest.expectYear(0);
        assertThat(missedTurnsAfterClosing(session)).containsExactly(1, 1);
    }

    @Test
    void rejoiningPlayerGetsTheTimeLeft() throws Exception {
        SessionActor session = running(LIVE_2);
        ServerMessage.Joined joined = joinedGuest;
        session.leave(guest);
        host.players();
        clock.advance(Duration.ofSeconds(50));
        RecordingPeer back = new RecordingPeer();

        session.rejoin(back, joined.player(), joined.token());

        back.joined();
        back.map();
        assertThat(back.phase()).isEqualTo(orders(0, Duration.ofSeconds(70)));
        session.close();
    }

    @Test
    void loadedWorldKeepsTheDeadlineOfItsYear() throws Exception {
        SessionActor first = running(LIVE_2);
        clock.advance(Duration.ofSeconds(50));
        first.leave(guest);
        first.leave(host);
        assertThat(first.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        clock.advance(Duration.ofSeconds(10));

        RecordingPeer back = new RecordingPeer();
        SessionActor loaded = session();
        loaded.load(back, "world-11", "Оля", Optional.of(new PlayerToken(1, hostToken)), false);
        back.joined();
        assertThat(back.lobby().setup().timer()).isEqualTo(LIVE_2);
        loaded.start(back);
        back.map();
        back.players();
        back.expectPhase(0, YearPhase.START_OF_YEAR);

        assertThat(back.phase()).isEqualTo(orders(0, Duration.ofSeconds(60)));
        loaded.close();
    }

    @Test
    void newTimerInTheLoadedLobbyRestartsTheYearClock() throws Exception {
        SessionActor first = running(LIVE_2);
        clock.advance(Duration.ofSeconds(50));
        first.leave(guest);
        first.leave(host);
        assertThat(first.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        TurnTimer async = new TurnTimer(TurnTimer.Mode.ASYNC, 12 * 60 * 60);

        RecordingPeer back = new RecordingPeer();
        SessionActor loaded = session();
        loaded.load(back, "world-11", "Оля", Optional.of(new PlayerToken(1, hostToken)), false);
        back.joined();
        back.lobby();
        loaded.setTimer(back, async);
        back.lobby();
        loaded.start(back);
        back.map();
        back.players();
        back.expectPhase(0, YearPhase.START_OF_YEAR);

        assertThat(back.phase()).isEqualTo(orders(0, Duration.ofHours(12)));
        loaded.leave(back);
        assertThat(loaded.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        try (WorldStore store = WorldStore.open(dir.resolve("world-11" + WorldStore.EXTENSION))) {
            assertThat(store.timer())
                    .isEqualTo(new SavedTimer(
                            async,
                            Optional.of(new SavedTimer.Deadline(0, clock.now().plus(Duration.ofHours(12))))));
        }
    }

    // ---- Допоміжне ----

    private String hostToken;
    private ServerMessage.Joined joinedGuest;

    private SessionActor session() {
        return new SessionActor(
                nextId++,
                TestServers.CONTENT,
                worlds,
                new PlayerTokens(),
                SessionActor.engine(TestServers.CONTENT),
                s -> {},
                clock);
    }

    /** Лобі нового світу з хостом «Оля» і гостем «Ігор»; обидва вже отримали останній стан лобі. */
    private SessionActor lobbyWithGuest() throws InterruptedException {
        SessionActor session = session();
        session.open(host, "Оля", 11, NpcShare.FEW);
        hostToken = host.joined().token();
        host.lobby();
        session.join(guest, "Ігор");
        joinedGuest = guest.joined();
        guest.lobby();
        host.lobby();
        return session;
    }

    /** Гра хоста й гостя з цим таймером, що чекає наказів року 0; межу року 0 перевірено. */
    private SessionActor running(TurnTimer timer) throws InterruptedException {
        SessionActor session = lobbyWithGuest();
        if (timer.timed()) {
            session.setTimer(host, timer);
            host.lobby();
            guest.lobby();
        }
        session.start(host);
        for (RecordingPeer peer : List.of(host, guest)) {
            peer.map();
            peer.players();
            peer.expectPhase(0, YearPhase.START_OF_YEAR);
            ServerMessage.Phase orders = peer.phase();
            assertThat(orders)
                    .isEqualTo(
                            timer.timed()
                                    ? orders(0, Duration.ofSeconds(timer.seconds()))
                                    : new ServerMessage.Phase(0, YearPhase.ORDERS));
        }
        return session;
    }

    private static ServerMessage.Phase orders(int turn, Duration left) {
        return new ServerMessage.Phase(turn, YearPhase.ORDERS, OptionalLong.of(left.toMillis()));
    }

    /** Пропуски гравців у файлі, коли сесію закрито. */
    private List<Integer> missedTurnsAfterClosing(SessionActor session) throws InterruptedException {
        session.close();
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        try (WorldStore store = WorldStore.open(dir.resolve("world-11" + WorldStore.EXTENSION))) {
            return store.players().stream().map(SavedPlayer::missedTurns).toList();
        }
    }
}
