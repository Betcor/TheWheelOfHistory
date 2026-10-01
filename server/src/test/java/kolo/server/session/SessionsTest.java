package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.server.TestServers;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
class SessionsTest {

    @TempDir
    Path worlds;

    @Test
    void sessionsGetConsecutiveIds() {
        Sessions sessions = new Sessions(new WorldDirectory(worlds));

        SessionActor first = sessions.create(TestServers.CONTENT);
        SessionActor second = sessions.create(TestServers.CONTENT);

        assertThat(first.id()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(2);
        assertThat(sessions.active()).containsExactly(first, second);
        assertThat(sessions.worlds().path()).isEqualTo(worlds);
        sessions.closeAll(30, TimeUnit.SECONDS);
    }

    @Test
    void openLobbiesAreFoundAndListed() throws Exception {
        Sessions sessions = new Sessions(new WorldDirectory(worlds));
        SessionActor empty = sessions.create(TestServers.CONTENT);
        SessionActor open = sessions.create(TestServers.CONTENT);
        RecordingPeer host = new RecordingPeer();
        open.open(host, "Оля", 4, NpcShare.MANY);
        String world = host.joined().world();
        host.lobby();

        assertThat(sessions.find(open.id())).contains(open);
        assertThat(sessions.find(99)).isEmpty();
        // Сесія без відкритого лобі в списку не з'являється.
        assertThat(sessions.lobbies())
                .containsExactly(new LobbyInfo(
                        open.id(), world, "Оля", 1, new LobbySetup.NewWorld(4, NpcShare.MANY, TurnTimer.MANUAL)));
        assertThat(empty.lobby()).isEmpty();
        sessions.closeAll(30, TimeUnit.SECONDS);
        assertThat(sessions.lobbies()).isEmpty();
    }

    @Test
    void closedSessionLeavesTheRegistry() throws Exception {
        Sessions sessions = new Sessions(new WorldDirectory(worlds));
        SessionActor session = sessions.create(TestServers.CONTENT);

        session.close();

        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(sessions.active()).isEmpty();
    }

    @Test
    void closeAllClosesWorldFilesAndRefusesNewSessions() throws Exception {
        Sessions sessions = new Sessions(new WorldDirectory(worlds));
        RecordingPeer host = new RecordingPeer();
        SessionActor session = sessions.create(TestServers.CONTENT);
        session.open(host, "Оля", 4, NpcShare.FEW);
        host.joined();
        host.lobby();
        session.start(host);
        RecordingPeer.enterNewWorld(session, host);

        sessions.closeAll(30, TimeUnit.SECONDS);

        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
        assertThat(sessions.active()).isEmpty();
        assertThatThrownBy(() -> sessions.create(TestServers.CONTENT)).isInstanceOf(IllegalStateException.class);
        // Файл закрито цілим: відкривається, журналу WAL поруч немає.
        Path file = worlds.resolve("world-4" + WorldStore.EXTENSION);
        assertThat(worlds.resolve("world-4" + WorldStore.EXTENSION + "-wal")).doesNotExist();
        try (WorldStore store = WorldStore.open(file)) {
            assertThat(store.lastTurn()).isZero();
        }
    }
}
