package kolo.client.net;

import kolo.engine.error.Checks;
import kolo.protocol.Protocol;

/**
 * Порти гри в локальній мережі: TCP-порт, на якому клієнт-хост відкриває гру, і UDP-порт пошуку — на ньому хост
 * відповідає, і туди ж клієнт розсилає запити.
 *
 * @param game TCP-порт гри; 0 — будь-який вільний
 * @param discovery UDP-порт пошуку; 0 — будь-який вільний (такого хоста пошуком не знайти — для тестів)
 */
public record LanPorts(int game, int discovery) {

    /** Порти гри: {@value Protocol#DEFAULT_PORT} і {@value Protocol#DISCOVERY_PORT}. */
    public static final LanPorts DEFAULT = new LanPorts(Protocol.DEFAULT_PORT, Protocol.DISCOVERY_PORT);

    /** Будь-які вільні порти. */
    public static final LanPorts ANY = new LanPorts(0, 0);

    public LanPorts {
        Checks.inRange("game", game, 0, 65_535);
        Checks.inRange("discovery", discovery, 0, 65_535);
    }
}
