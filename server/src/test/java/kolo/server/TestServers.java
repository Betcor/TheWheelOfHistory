package kolo.server;

import java.util.List;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.engine.view.MapView;
import kolo.engine.view.MapViews;
import kolo.server.session.NewWorlds;

/** Вбудований контент і карти світів з нього — спільні для тестів сервера, бо завантаження недешеве. */
public final class TestServers {

    public static final ContentPack CONTENT = ContentLoader.loadBundled();

    /** Таймери ходу вбудованого контенту, з яких обирає хост. */
    public static final List<TurnTimer> TIMERS = CONTENT.balance().timers().choices();

    private TestServers() {}

    /** Карта світу, яку сервер надсилає при старті гри з {@code players} гравцями. */
    public static MapView map(long seed, int players, NpcShare npcShare) {
        return MapViews.of(NewWorlds.generate(CONTENT, seed, players, npcShare));
    }
}
