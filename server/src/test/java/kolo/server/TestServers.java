package kolo.server;

import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.engine.view.MapViews;
import kolo.server.session.NewWorlds;

/** Вбудований контент і карти світів з нього — спільні для тестів сервера, бо завантаження недешеве. */
public final class TestServers {

    public static final ContentPack CONTENT = ContentLoader.loadBundled();

    private TestServers() {}

    /** Карта світу, яку сервер надсилає на {@code CreateWorld}. */
    public static MapView map(long seed, int players, NpcShare npcShare) {
        return MapViews.of(NewWorlds.generate(CONTENT, seed, players, npcShare));
    }
}
