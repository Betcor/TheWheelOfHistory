package kolo.client.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import kolo.client.map.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ProtocolException;
import kolo.engine.error.VersionMismatchException;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.protocol.Protocol;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.ServerMessage;
import org.junit.jupiter.api.Test;

/** Клієнтський бік розмови в {@link EmbeddedChannel} без кодека: повідомлення — об'єкти. */
class ConnectionHandlerTest {

    private static final String HASH = "e".repeat(64);
    private static final ClientMessage.CreateWorld REQUEST = new ClientMessage.CreateWorld(1, 1, NpcShare.FEW);

    private final ConnectionHandler handler = new ConnectionHandler(HASH);
    private final EmbeddedChannel channel = new EmbeddedChannel(handler);

    @Test
    void welcomeWithSameVersionCompletesHandshake() {
        channel.writeInbound(new ServerMessage.Welcome(Protocol.VERSION, HASH));

        assertThat(handler.welcomed()).isCompleted();
    }

    @Test
    void welcomeWithOtherContentFailsHandshakeAndCloses() {
        channel.writeInbound(new ServerMessage.Welcome(Protocol.VERSION, "f".repeat(64)));

        assertThatThrownBy(() -> handler.welcomed().get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(VersionMismatchException.class);
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void serverErrorBeforeWelcomeFailsHandshake() {
        channel.writeInbound(error(ErrorCode.PROTOCOL_ERROR));

        assertThatThrownBy(() -> handler.welcomed().get()).hasCauseInstanceOf(ServerErrorException.class);
    }

    @Test
    void mapArrivesInChunks() throws Exception {
        CompletableFuture<MapView> map = request();

        assertThat(channel.<ClientMessage>readOutbound()).isEqualTo(REQUEST);
        for (ServerMessage part : MapChunks.split(TestMaps.MAP, 2)) {
            assertThat(map).isNotDone();
            channel.writeInbound(part);
        }

        assertThat(map.get()).isEqualTo(TestMaps.MAP);
        // Наступний запит після отриманої карти дозволено.
        assertThat(request()).isNotDone();
    }

    @Test
    void serverErrorFailsTheRequestButKeepsTheConnection() {
        CompletableFuture<MapView> map = request();

        channel.writeInbound(error(ErrorCode.VALUE_OUT_OF_RANGE));

        assertThatThrownBy(map::get).hasCauseInstanceOf(ServerErrorException.class);
        assertThat(((ServerErrorException) map.exceptionNow()).error().code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void secondRequestWhileWaitingIsRejected() {
        request();

        assertThatThrownBy(() -> request().get()).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void mapWithoutRequestBreaksTheConnection() {
        welcome();

        channel.writeInbound(MapChunks.split(TestMaps.MAP).getFirst());

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void chunkOutOfOrderFailsTheRequest() {
        CompletableFuture<MapView> map = request();
        List<ServerMessage> parts = MapChunks.split(TestMaps.MAP, 2);

        channel.writeInbound(parts.getFirst());
        channel.writeInbound(parts.get(2));

        assertThatThrownBy(map::get).hasCauseInstanceOf(ProtocolException.class);
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void disconnectFailsWaitingRequest() {
        CompletableFuture<MapView> map = request();

        channel.close();

        assertThatThrownBy(map::get).hasCauseInstanceOf(ConnectionClosedException.class);
    }

    @Test
    void disconnectBeforeWelcomeFailsHandshake() {
        channel.close();

        assertThatThrownBy(() -> handler.welcomed().get()).hasCauseInstanceOf(ConnectionClosedException.class);
    }

    @Test
    void requestOnClosedChannelFails() {
        welcome();
        channel.close();

        assertThatThrownBy(() -> request().get()).hasCauseInstanceOf(ConnectionClosedException.class);
    }

    private void welcome() {
        if (!handler.welcomed().isDone()) {
            channel.writeInbound(new ServerMessage.Welcome(Protocol.VERSION, HASH));
        }
    }

    private CompletableFuture<MapView> request() {
        welcome();
        CompletableFuture<MapView> result = new CompletableFuture<>();
        handler.createWorld(channel, REQUEST, result);
        return result;
    }

    private static ServerMessage.Error error(ErrorCode code) {
        return new ServerMessage.Error(code, new TreeMap<>(Map.of("field", "players")));
    }
}
