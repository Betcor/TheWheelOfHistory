package kolo.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kolo.engine.view.MapView;
import kolo.protocol.codec.ProtocolPipeline;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.MapAssembler;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Протокол через справжній {@link LocalChannel} — транспорт одиночної гри: рукостискання, запит світу й карта
 * частинами. Сервер тут — ручний обробник, не сесія.
 */
class ProtocolSmokeTest {

    private static final MapView MAP = TestMessages.map(7, 2500);

    @Test
    @Timeout(20)
    void handshakeAndMapOverLocalChannel() throws Exception {
        // Свої потоки в сервера й клієнта, як у грі: вбудований сервер не ділить event loop з клієнтом.
        EventLoopGroup serverGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        EventLoopGroup clientGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        try {
            LocalAddress address = new LocalAddress("kolo-protocol-smoke");
            CompletableFuture<MapView> received = new CompletableFuture<>();
            Channel server = new ServerBootstrap()
                    .group(serverGroup)
                    .channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<LocalChannel>() {
                        @Override
                        protected void initChannel(LocalChannel channel) {
                            ProtocolPipeline.server(channel.pipeline());
                            channel.pipeline().addLast(new ToyServer(received));
                        }
                    })
                    .bind(address)
                    .sync()
                    .channel();

            Channel client = new Bootstrap()
                    .group(clientGroup)
                    .channel(LocalChannel.class)
                    .handler(new ChannelInitializer<LocalChannel>() {
                        @Override
                        protected void initChannel(LocalChannel channel) {
                            ProtocolPipeline.client(channel.pipeline());
                            channel.pipeline().addLast(new ToyClient(received));
                        }
                    })
                    .connect(address)
                    .sync()
                    .channel();

            client.writeAndFlush(Handshake.hello(TestMessages.HASH)).sync();

            assertThat(received.get(15, TimeUnit.SECONDS)).isEqualTo(MAP);
            client.close().sync();
            server.close().sync();
        } finally {
            clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
            serverGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    /** Вітає клієнта й на запит світу віддає тестову карту частинами. */
    private static final class ToyServer extends SimpleChannelInboundHandler<ClientMessage> {

        private final CompletableFuture<MapView> failures;

        ToyServer(CompletableFuture<MapView> failures) {
            this.failures = failures;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            failures.completeExceptionally(new AssertionError("сервер", cause));
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ClientMessage message) {
            switch (message) {
                case ClientMessage.Hello hello -> ctx.writeAndFlush(Handshake.accept(hello, TestMessages.HASH));
                case ClientMessage.CreateWorld create -> {
                    for (ServerMessage part : MapChunks.split(MAP, 300)) {
                        ctx.write(part);
                    }
                    ctx.writeAndFlush(new ServerMessage.Phase(0, YearPhase.ORDERS));
                }
                case ClientMessage.Ready ready ->
                    ctx.writeAndFlush(new ServerMessage.Phase(ready.turn() + 1, YearPhase.ORDERS));
            }
        }
    }

    /** Після привітання просить світ і збирає карту. */
    private static final class ToyClient extends SimpleChannelInboundHandler<ServerMessage> {

        private final CompletableFuture<MapView> received;
        private final MapAssembler assembler = new MapAssembler();
        private MapView map;

        ToyClient(CompletableFuture<MapView> received) {
            this.received = received;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ServerMessage message) {
            switch (message) {
                case ServerMessage.Welcome welcome -> {
                    Handshake.confirm(welcome, TestMessages.HASH);
                    ctx.writeAndFlush(new ClientMessage.CreateWorld(7, 1, kolo.engine.state.NpcShare.FEW));
                }
                case ServerMessage.MapStart start -> assembler.start(start);
                case ServerMessage.MapCells cells -> assembler.add(cells).ifPresent(done -> map = done);
                // Карта прийшла — закінчуємо рік 0; рік 1 означає, що сервер прийняв «Готово».
                case ServerMessage.Phase phase -> {
                    if (phase.turn() == 0 && map != null) {
                        ctx.writeAndFlush(new ClientMessage.Ready(0));
                    } else if (phase.turn() == 1) {
                        received.complete(map);
                    }
                }
                case ServerMessage.Error error ->
                    received.completeExceptionally(new AssertionError("помилка сервера " + error));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            received.completeExceptionally(new AssertionError("клієнт", cause));
        }
    }
}
