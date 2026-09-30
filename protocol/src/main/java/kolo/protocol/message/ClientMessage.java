package kolo.protocol.message;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.NpcShare;

/** Повідомлення клієнта серверу. */
public sealed interface ClientMessage permits ClientMessage.Hello, ClientMessage.CreateWorld {

    /**
     * Перше повідомлення з'єднання: клієнт називає свою версію протоколу й хеш контенту. Сервер відповідає {@link
     * ServerMessage.Welcome} або {@link ServerMessage.Error} з {@code VERSION_MISMATCH} ({@link Handshake}).
     *
     * @param protocolVersion версія протоколу клієнта
     * @param contentHash хеш контенту клієнта ({@code ContentPack.hash()})
     */
    record Hello(int protocolVersion, String contentHash) implements ClientMessage {

        public Hello {
            Checks.inRange("protocol_version", protocolVersion, 1, Integer.MAX_VALUE);
            Checks.notBlank("content_hash", contentHash);
        }
    }

    /**
     * Створити новий світ. Сервер відповідає картою ({@link ServerMessage.MapStart} і частини {@link
     * ServerMessage.MapCells}) або {@link ServerMessage.Error}; межі параметрів перевіряє сервер.
     *
     * @param seed seed світу (GD §3.4)
     * @param players кількість гравців
     * @param npcShare частка NPC-держав
     */
    record CreateWorld(long seed, int players, NpcShare npcShare) implements ClientMessage {

        public CreateWorld {
            Objects.requireNonNull(npcShare, "npcShare");
        }
    }
}
