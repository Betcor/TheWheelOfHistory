package kolo.client.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kolo.engine.error.Checks;
import kolo.protocol.Protocol;
import kolo.protocol.discovery.DiscoveryPacket;
import kolo.protocol.discovery.DiscoveryPackets;

/**
 * Пошук гри в локальній мережі (ADR 0050): запит ({@link DiscoveryPacket.Query}) — на широкомовну адресу кожного
 * мережевого інтерфейсу IPv4, на загальну {@code 255.255.255.255} і на петлю (гра на цьому ж комп'ютері), відповіді
 * збираються {@link #WAIT}. Запит повторюється посередині очікування: UDP може загубити датаграму.
 *
 * <p>Блокує на час очікування — викликати у фоні. Брандмауер може не пропустити ні запит, ні відповідь: тоді гра не
 * знайдеться, і до неї підключаються за адресою.
 */
public final class LanSearch {

    /** Скільки чекати відповідей. */
    public static final Duration WAIT = Duration.ofSeconds(1);

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private final int port;
    private final Duration wait;

    /**
     * @param port UDP-порт пошуку, на якому відповідають ігри
     * @param wait скільки чекати відповідей
     */
    public LanSearch(int port, Duration wait) {
        this.port = Checks.inRange("port", port, 1, 65_535);
        if (wait.isNegative() || wait.isZero()) {
            throw new IllegalArgumentException("wait має бути додатним: " + wait);
        }
        this.wait = wait;
    }

    /**
     * Розсилає запит і збирає відповіді.
     *
     * @return ігри, що відповіли, кожна раз, за адресою; гра на цьому комп'ютері — за адресою петлі
     * @throws LanSearchUnavailableException якщо запит не вдалося надіслати нікуди
     */
    public List<LanHost> find() {
        byte[] query = DiscoveryPackets.encode(new DiscoveryPacket.Query(Protocol.VERSION));
        List<InetAddress> targets = targets();
        Map<InetSocketAddress, LanHost> found = new LinkedHashMap<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            long start = System.nanoTime();
            long deadline = start + wait.toNanos();
            long repeatAt = start + wait.toNanos() / 2;
            boolean repeated = false;
            send(socket, query, targets);
            byte[] buffer = new byte[DiscoveryPackets.MAX_BYTES + 1];
            for (long now = start; now < deadline; now = System.nanoTime()) {
                if (!repeated && now >= repeatAt) {
                    sendQuietly(socket, query, targets);
                    repeated = true;
                }
                long until = repeated ? deadline : repeatAt;
                socket.setSoTimeout(
                        (int) Math.max(1, Duration.ofNanos(until - now).toMillis()));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    continue;
                }
                host(packet).ifPresent(host -> found.putIfAbsent(host.server(), host));
            }
        } catch (IOException e) {
            throw new LanSearchUnavailableException("пошук у локальній мережі недоступний", e);
        }
        List<LanHost> hosts = new ArrayList<>(found.values());
        hosts.sort(Comparator.comparing(
                        (LanHost host) -> host.server().getAddress().getHostAddress())
                .thenComparingInt(host -> host.server().getPort()));
        return hosts;
    }

    /** Куди розсилати запит: петля, широкомовні адреси інтерфейсів IPv4 і загальна широкомовна. */
    static List<InetAddress> targets() {
        Set<InetAddress> targets = new LinkedHashSet<>();
        targets.add(LOOPBACK);
        try {
            for (NetworkInterface face : NetworkInterface.networkInterfaces().toList()) {
                if (!face.isUp() || face.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress address : face.getInterfaceAddresses()) {
                    if (address.getBroadcast() != null) {
                        targets.add(address.getBroadcast());
                    }
                }
            }
        } catch (SocketException e) {
            // Інтерфейси не прочиталися — лишаються петля й загальна широкомовна адреса.
        }
        try {
            targets.add(InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255}));
        } catch (IOException e) {
            throw new IllegalStateException("адреса IPv4 з чотирьох байтів", e);
        }
        return List.copyOf(targets);
    }

    /** Перша розсилка: якщо запит не пішов нікуди, шукати нема як. */
    private void send(DatagramSocket socket, byte[] query, List<InetAddress> targets) throws IOException {
        IOException last = null;
        boolean sent = false;
        for (InetAddress target : targets) {
            try {
                socket.send(new DatagramPacket(query, query.length, target, port));
                sent = true;
            } catch (IOException e) {
                // Окрема мережа недосяжна (інтерфейс без маршруту) — решта ще може відповісти.
                last = e;
            }
        }
        if (!sent) {
            throw last;
        }
    }

    private void sendQuietly(DatagramSocket socket, byte[] query, List<InetAddress> targets) {
        for (InetAddress target : targets) {
            try {
                socket.send(new DatagramPacket(query, query.length, target, port));
            } catch (IOException e) {
                // Як і в першій розсилці: недосяжна мережа не заважає іншим.
            }
        }
    }

    /** Гра з відповіді; чуже й пошкоджене — порожньо. */
    private static Optional<LanHost> host(DatagramPacket packet) {
        if (!(DiscoveryPackets.decode(packet.getData(), packet.getLength()).orElse(null)
                instanceof DiscoveryPacket.Reply reply)) {
            return Optional.empty();
        }
        InetAddress sender = packet.getAddress();
        InetAddress address = isLocal(sender) ? LOOPBACK : sender;
        return Optional.of(new LanHost(new InetSocketAddress(address, reply.port()), reply.version()));
    }

    /** Чи це адреса цього комп'ютера: одна гра відповідає і з петлі, і з адреси в мережі — це та сама гра. */
    private static boolean isLocal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            return true;
        }
        try {
            return address instanceof Inet4Address && NetworkInterface.getByInetAddress(address) != null;
        } catch (SocketException e) {
            return false;
        }
    }
}
