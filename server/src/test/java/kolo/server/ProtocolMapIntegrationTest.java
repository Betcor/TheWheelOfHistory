package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Optional;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import kolo.engine.view.MapView;
import kolo.protocol.Protocol;
import kolo.protocol.codec.MessageJson;
import kolo.protocol.codec.ProtocolPipeline;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.MapAssembler;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.ServerMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Протокол на справжніх світах з вбудованого контенту: карта будь-якого розміру проходить частинами через обидві
 * сторони каналу без втрат, кожен фрейм — у межі протоколу, а найбільша карта — у бюджеті часу.
 */
class ProtocolMapIntegrationTest {

    @Test
    void everyWorldMapCrossesTheWire() {
        for (long seed = 0; seed < 6; seed++) {
            int players = 1 + (int) (seed * 5 % WorldLimits.MAX_PLAYERS);
            NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
            MapView map = TestServers.map(seed, players, share);

            assertThat(transfer(MapChunks.split(map))).isEqualTo(map);
        }
    }

    @Test
    void largestMapFramesStayWellBelowTheLimit() {
        MapView map = TestServers.map(1, WorldLimits.MAX_PLAYERS, NpcShare.MANY);

        int largest = 0;
        for (ServerMessage message : MapChunks.split(map)) {
            largest = Math.max(largest, MessageJson.write(message).length);
        }

        // Запас учетверо: форма комірок і довші назви не мусять упертися в межу фрейму.
        assertThat(largest).isLessThan(Protocol.MAX_FRAME_BYTES / 4);
    }

    @Test
    void handshakeWithBundledContentHash() {
        String hash = TestServers.CONTENT.hash();

        ServerMessage.Welcome welcome = Handshake.accept(Handshake.hello(hash), hash);

        Handshake.confirm(welcome, hash);
        assertThat(MessageJson.readServer(MessageJson.write(welcome))).isEqualTo(welcome);
        assertThat(MessageJson.readClient(MessageJson.write(new ClientMessage.CreateWorld(1, 16, NpcShare.MANY))))
                .isEqualTo(new ClientMessage.CreateWorld(1, 16, NpcShare.MANY));
    }

    @Test
    @Tag("budget")
    void largestMapFitsBudget() {
        MapView map = TestServers.map(1, WorldLimits.MAX_PLAYERS, NpcShare.MANY);

        Budget.Timed<MapView> sent = Budget.best(() -> transfer(MapChunks.split(map)));

        assertThat(sent.result()).isEqualTo(map);
        // Карта надсилається раз на з'єднання; генерація світу (≤ 2 с) — довша.
        assertThat(sent.millis()).isLessThan(500);
    }

    /** Повідомлення сервера → байти → клієнт → зібрана карта. */
    private static MapView transfer(List<ServerMessage> messages) {
        EmbeddedChannel server = new EmbeddedChannel();
        ProtocolPipeline.server(server.pipeline());
        EmbeddedChannel client = new EmbeddedChannel();
        ProtocolPipeline.client(client.pipeline());
        MapAssembler assembler = new MapAssembler();
        Optional<MapView> result = Optional.empty();

        for (ServerMessage message : messages) {
            server.writeOutbound(message);
            for (ByteBuf frame = server.readOutbound(); frame != null; frame = server.readOutbound()) {
                client.writeInbound(frame);
            }
            for (Object read = client.readInbound(); read != null; read = client.readInbound()) {
                switch ((ServerMessage) read) {
                    case ServerMessage.MapStart start -> assembler.start(start);
                    case ServerMessage.MapCells cells -> result = assembler.add(cells);
                    default -> throw new AssertionError(read);
                }
            }
        }
        server.finishAndReleaseAll();
        client.finishAndReleaseAll();
        return result.orElseThrow();
    }
}
