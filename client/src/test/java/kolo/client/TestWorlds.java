package kolo.client;

import kolo.client.net.EmbeddedGame;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;

/**
 * Світи з вбудованого сервера на вбудованому контенті — спільні для тестів клієнта, бо генерація недешева. Карти
 * приходять так само, як у грі: повідомленнями через {@code LocalChannel}. Файли світів — у домашній теці тестів
 * ({@code kolo.home} від збірки).
 */
public final class TestWorlds {

    public static final EmbeddedGame GAME = EmbeddedGame.start();

    /** Світ за замовчуванням екрана нового світу: 1 гравець, звичайна частка NPC. */
    public static final MapView DEFAULT =
            GAME.newWorld(1970, 1, NpcShare.NORMAL).map();

    private TestWorlds() {}

    /** Найбільший світ: 16 гравців і багато NPC. */
    public static MapView largest(long seed) {
        return GAME.newWorld(seed, 16, NpcShare.MANY).map();
    }
}
