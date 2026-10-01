package kolo.protocol.discovery;

import kolo.engine.error.Checks;

/**
 * Датаграма пошуку гри в локальній мережі: клієнт розсилає {@link Query} на {@link
 * kolo.protocol.Protocol#DISCOVERY_PORT}, сервер відповідає {@link Reply} напряму відправникові. Адреса гри — IP
 * відправника відповіді й TCP-порт з неї. Байти — {@link DiscoveryPackets}.
 */
public sealed interface DiscoveryPacket {

    /** Версія протоколу відправника: клієнт відрізняє несумісну гру, не з'єднуючись із нею. */
    int version();

    /**
     * Запит «хто тут грає».
     *
     * @param version версія протоколу клієнта
     */
    record Query(int version) implements DiscoveryPacket {}

    /**
     * Відповідь сервера.
     *
     * @param version версія протоколу сервера
     * @param port TCP-порт гри
     */
    record Reply(int version, int port) implements DiscoveryPacket {

        public Reply {
            Checks.inRange("port", port, 1, 65_535);
        }
    }
}
