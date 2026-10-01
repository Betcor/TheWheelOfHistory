package kolo.client.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Відповідач пошуку на петлі для тестів: на кожну датаграму відповідає заданими байтами. */
final class FakeResponder implements AutoCloseable {

    private final DatagramSocket socket;
    private final Thread thread;
    private final AtomicInteger received = new AtomicInteger();

    /** @param replies датаграми у відповідь на кожну отриману, по черзі */
    FakeResponder(List<byte[]> replies) throws SocketException {
        socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        thread = Thread.ofVirtual().start(() -> {
            byte[] buffer = new byte[64];
            while (!socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    received.incrementAndGet();
                    for (byte[] reply : replies) {
                        socket.send(new DatagramPacket(reply, reply.length, packet.getSocketAddress()));
                    }
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    int port() {
        return socket.getLocalPort();
    }

    /** Скільки датаграм отримано. */
    int received() {
        return received.get();
    }

    @Override
    public void close() {
        socket.close();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
