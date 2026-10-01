package kolo.client.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.channel.local.LocalAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import kolo.client.TestWorlds;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.protocol.message.Handshake;
import kolo.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Клієнт ↔ вбудований сервер через {@code LocalChannel}: рукостискання, світ, роки, помилки сервера й розрив. */
@Timeout(60)
class ServerConnectionTest {

    @TempDir
    static Path worlds;

    private static EmbeddedServer SERVER;

    @BeforeAll
    static void start() {
        SERVER = EmbeddedServer.startWithBundledContent(worlds);
    }

    @AfterAll
    static void stop() {
        SERVER.close();
    }

    @Test
    void sameSeedSameMap() {
        try (ServerConnection connection = connect()) {
            MapView first = EmbeddedGame.await(connection.createWorld(42, 2, NpcShare.NORMAL))
                    .map();
            MapView second = EmbeddedGame.await(connection.createWorld(42, 2, NpcShare.NORMAL))
                    .map();

            assertThat(second).isEqualTo(first);
            assertThat(first.seed()).isEqualTo(42);
            assertThat(first.countries().stream().filter(country -> country.player()))
                    .hasSize(2);
        }
    }

    @Test
    void serverErrorKeepsTheConnectionUsable() {
        try (ServerConnection connection = connect()) {
            assertThatThrownBy(() -> EmbeddedGame.await(connection.createWorld(1, 0, NpcShare.FEW)))
                    .isInstanceOfSatisfying(ServerErrorException.class, e -> {
                        assertThat(e.error().code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                        assertThat(e.error().details()).containsEntry("field", "players");
                    });

            assertThat(connection.isOpen()).isTrue();
            assertThat(EmbeddedGame.await(connection.createWorld(1, 1, NpcShare.FEW))
                            .map()
                            .cells())
                    .isNotEmpty();
        }
    }

    @Test
    void yearsFollowEachOther() {
        try (ServerConnection connection = connect()) {
            GameStart start = EmbeddedGame.await(connection.createWorld(5, 1, NpcShare.FEW));
            assertThat(start.turn()).isZero();

            assertThat(EmbeddedGame.await(connection.endYear(0))).isEqualTo(1);
            assertThat(EmbeddedGame.await(connection.endYear(1))).isEqualTo(2);

            assertThatThrownBy(() -> EmbeddedGame.await(connection.endYear(0)))
                    .isInstanceOfSatisfying(
                            ServerErrorException.class,
                            e -> assertThat(e.error().code()).isEqualTo(ErrorCode.PHASE_CLOSED));
            assertThat(connection.isOpen()).isTrue();
        }
    }

    @Test
    void otherContentIsRejectedByTheServer() {
        CompletableFuture<ServerConnection> connecting = ServerConnection.connect(SERVER.address(), "0".repeat(64));

        assertThatThrownBy(() -> EmbeddedGame.await(connecting))
                .isInstanceOfSatisfying(ServerErrorException.class, e -> {
                    assertThat(e.error().code()).isEqualTo(ErrorCode.VERSION_MISMATCH);
                    assertThat(e.error().details()).containsEntry("part", Handshake.CONTENT);
                });
    }

    @Test
    void noServerAtAddress() {
        CompletableFuture<ServerConnection> connecting =
                ServerConnection.connect(new LocalAddress("kolo-nobody"), SERVER.contentHash());

        assertThatThrownBy(() -> EmbeddedGame.await(connecting)).isInstanceOf(ConnectionClosedException.class);
    }

    @Test
    void stoppedServerFailsRequests() {
        EmbeddedServer server = EmbeddedServer.startWithBundledContent(worlds);
        ServerConnection connection =
                EmbeddedGame.await(ServerConnection.connect(server.address(), server.contentHash()));

        server.close();

        assertThatThrownBy(() -> EmbeddedGame.await(connection.createWorld(1, 1, NpcShare.FEW)))
                .isInstanceOf(ConnectionClosedException.class);
        connection.close();
        assertThat(connection.isOpen()).isFalse();
    }

    @Test
    void closedConnectionFailsRequests() {
        ServerConnection connection = connect();
        connection.close();

        assertThatThrownBy(() -> EmbeddedGame.await(connection.createWorld(1, 1, NpcShare.FEW)))
                .isInstanceOf(ConnectionClosedException.class);
    }

    @Test
    void embeddedGameServesTheSameWorlds() {
        assertThat(TestWorlds.DEFAULT.seed()).isEqualTo(1970);
        try (ServerConnection connection = connect()) {
            assertThat(EmbeddedGame.await(connection.createWorld(1970, 1, NpcShare.NORMAL))
                            .map())
                    .isEqualTo(TestWorlds.DEFAULT);
        }
    }

    private static ServerConnection connect() {
        return EmbeddedGame.await(ServerConnection.connect(SERVER.address(), SERVER.contentHash()));
    }
}
