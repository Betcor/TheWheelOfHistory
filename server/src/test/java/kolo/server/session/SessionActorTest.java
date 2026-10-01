package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import kolo.protocol.message.ServerMessage;
import kolo.server.TestServers;
import kolo.server.persistence.SavedTurn;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Стан-машина сесії без мережі: генерація, фази року, «Готово», запис року, пауза й закриття. */
@Timeout(60)
class SessionActorTest {

    @TempDir
    Path worlds;

    private final RecordingPeer host = new RecordingPeer();
    private final AtomicInteger closed = new AtomicInteger();

    @Test
    void newSessionIsInTheLobby() {
        SessionActor session = session(SessionActor.engine(TestServers.CONTENT));

        assertThat(session.state()).isEqualTo(SessionState.LOBBY);
        assertThat(session.id()).isEqualTo(7);
        session.close();
    }

    @Test
    void generatedWorldIsSavedAndItsFirstYearOpens() throws Exception {
        SessionActor session = running(SessionActor.engine(TestServers.CONTENT));

        assertThat(session.state()).isEqualTo(SessionState.RUNNING);
        Path file = worlds.resolve("world-11" + WorldStore.EXTENSION);
        assertThat(file).exists();
        leaveAndAwait(session);
        try (WorldStore store = WorldStore.open(file)) {
            assertThat(store.meta().name()).isEqualTo("world-11");
            assertThat(store.meta().seed()).isEqualTo(11);
            assertThat(store.lastTurn()).isZero();
        }
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
        SessionActor session = session(SessionActor.engine(TestServers.CONTENT));
        session.generate(host, 11, 1, NpcShare.FEW);
        // Завдання сесії виконуються по черзі: «Готово» дійде після генерації — уже в році 0.
        session.ready(host, 0);

        host.map();
        host.expectOrders(0);
        host.expectYear(0);
        session.close();
    }

    @Test
    void failedYearPausesTheSessionAndKeepsTheFile() throws Exception {
        UnaryOperator<WorldState> broken = state -> {
            throw new InvariantViolationException(ErrorDetails.of("check", "test"));
        };
        SessionActor session = running(broken);

        session.ready(host, 0);

        host.expectPhase(0, kolo.protocol.message.YearPhase.RESOLVING);
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
    void invalidWorldIsReportedAndClosesTheSession() throws Exception {
        SessionActor session = session(SessionActor.engine(TestServers.CONTENT));

        session.generate(host, 1, 0, NpcShare.FEW);

        assertThat(host.error().code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
        assertThat(closed).hasValue(1);
        assertThat(host.closed()).isFalse();
        try (Stream<Path> files = Files.list(worlds)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void unwritableDirectoryIsReportedAndClosesTheSession() throws Exception {
        Path notADirectory = Files.writeString(worlds.resolve("file"), "x");
        SessionActor session = new SessionActor(
                1,
                TestServers.CONTENT,
                new WorldDirectory(notADirectory),
                SessionActor.engine(TestServers.CONTENT),
                s -> {
                    closed.incrementAndGet();
                });

        session.generate(host, 1, 1, NpcShare.FEW);

        ServerMessage.Error error = host.error();
        assertThat(error.code()).isEqualTo(ErrorCode.SAVE_FILE_ERROR);
        assertThat(error.details()).containsEntry("operation", "create_directory");
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
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
    void strangerIsIgnored() throws Exception {
        SessionActor session = running(SessionActor.engine(TestServers.CONTENT));
        RecordingPeer stranger = new RecordingPeer();

        session.ready(stranger, 0);
        session.leave(stranger);

        assertThat(stranger.quiet()).isTrue();
        assertThat(host.quiet()).isTrue();
        assertThat(session.state()).isEqualTo(SessionState.RUNNING);
        session.close();
    }

    @Test
    void serverBugClosesTheSessionAndItsConnections() throws Exception {
        UnaryOperator<WorldState> buggy = state -> {
            throw new IllegalStateException("баг сервера");
        };
        SessionActor session = running(buggy);

        session.ready(host, 0);

        host.expectPhase(0, kolo.protocol.message.YearPhase.RESOLVING);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(host.closed()).isTrue();
        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
    }

    @Test
    void sessionRunsInItsOwnThread() throws Exception {
        List<String> threads = new java.util.concurrent.CopyOnWriteArrayList<>();
        SessionActor session = running(state -> {
            threads.add(Thread.currentThread().getName());
            return SessionActor.engine(TestServers.CONTENT).apply(state);
        });

        session.ready(host, 0);
        host.expectYear(0);

        assertThat(threads).containsExactly("kolo-session-7");
        session.close();
    }

    private SessionActor session(UnaryOperator<WorldState> years) {
        return new SessionActor(
                7, TestServers.CONTENT, new WorldDirectory(worlds), years, s -> closed.incrementAndGet());
    }

    /** Сесія з малим світом, що чекає наказів року 0. */
    private SessionActor running(UnaryOperator<WorldState> years) throws InterruptedException {
        SessionActor session = session(years);
        session.generate(host, 11, 1, NpcShare.FEW);
        assertThat(host.map()).isEqualTo(TestServers.map(11, 1, NpcShare.FEW));
        host.expectOrders(0);
        return session;
    }

    private void leaveAndAwait(SessionActor session) throws InterruptedException {
        session.leave(host);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
    }
}
