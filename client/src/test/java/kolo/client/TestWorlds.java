package kolo.client;

import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.server.EmbeddedServer;

/** Світи з вбудованого сервера на вбудованому контенті — спільні для тестів клієнта, бо генерація недешева. */
public final class TestWorlds {

    public static final EmbeddedServer SERVER = EmbeddedServer.withBundledContent();

    /** Світ за замовчуванням екрана нового світу: 1 гравець, звичайна частка NPC. */
    public static final MapView DEFAULT = SERVER.newWorld(1970, 1, NpcShare.NORMAL);

    private TestWorlds() {}

    /** Найбільший світ: 16 гравців і багато NPC. */
    public static MapView largest(long seed) {
        return SERVER.newWorld(seed, 16, NpcShare.MANY);
    }
}
