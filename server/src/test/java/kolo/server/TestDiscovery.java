package kolo.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Optional;
import kolo.protocol.discovery.DiscoveryPacket;
import kolo.protocol.discovery.DiscoveryPackets;

/** Клієнт пошуку для тестів: один запит на петлю й одна відповідь. */
public final class TestDiscovery {

    /** Скільки чекати відповіді, мс: на петлі вона приходить за мілісекунди. */
    private static final int WAIT_MILLIS = 2000;

    /** Скільки чекати там, де відповіді не має бути, мс. */
    public static final int SILENCE_MILLIS = 300;

    private TestDiscovery() {}

    /** Надсилає запит версії {@code version} на порт адреси через петлю й чекає відповідь. */
    public static Optional<DiscoveryPacket> ask(InetSocketAddress responder, int version) throws IOException {
        return send(responder, DiscoveryPackets.encode(new DiscoveryPacket.Query(version)), WAIT_MILLIS);
    }

    /** Надсилає довільні байти й чекає відповідь {@code waitMillis}; тиша — порожньо. */
    public static Optional<DiscoveryPacket> send(InetSocketAddress responder, byte[] bytes, int waitMillis)
            throws IOException {
        InetSocketAddress target = new InetSocketAddress(InetAddress.getLoopbackAddress(), responder.getPort());
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            socket.setSoTimeout(waitMillis);
            socket.send(new DatagramPacket(bytes, bytes.length, target));
            byte[] buffer = new byte[64];
            DatagramPacket received = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(received);
            } catch (SocketTimeoutException e) {
                return Optional.empty();
            }
            return DiscoveryPackets.decode(buffer, received.getLength());
        }
    }
}
