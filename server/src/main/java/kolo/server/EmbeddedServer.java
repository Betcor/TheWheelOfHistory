package kolo.server;

import java.util.function.Supplier;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.world.StartWorld;
import kolo.engine.generation.world.WorldGenerator;
import kolo.engine.generation.world.WorldStates;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import kolo.engine.view.MapView;
import kolo.engine.view.MapViews;

/**
 * Вбудований сервер для одиночної гри, hot-seat і LAN-хоста.
 *
 * <p>Поки мережі й сесій немає, клієнт викликає його напряму: згенерувати світ і отримати карту для показу. З мережею
 * ці виклики стануть повідомленнями протоколу через {@code LocalChannel}, а клієнт і далі бачитиме лише представлення
 * світу ({@link MapView}), а не стан.
 *
 * <p>Контент завантажується при першому зверненні, а не при створенні: старт клієнта до меню його не чекає.
 * Потокобезпечний: клієнт звертається з фонового потоку.
 */
public final class EmbeddedServer {

    private final Supplier<ContentPack> source;
    private ContentPack content;

    private EmbeddedServer(Supplier<ContentPack> source) {
        this.source = source;
    }

    /** Сервер із вбудованим контентом гри. */
    public static EmbeddedServer withBundledContent() {
        return new EmbeddedServer(ContentLoader::loadBundled);
    }

    /**
     * Генерує новий світ і повертає його карту. Той самий seed і параметри — та сама карта.
     *
     * @param seed seed світу (GD §3.4)
     * @param players кількість гравців
     * @param npcShare частка NPC-держав
     * @throws kolo.engine.error.ContentException якщо вбудований контент невалідний
     * @throws kolo.engine.error.ValidationException якщо параметри світу поза межами
     * @throws kolo.engine.error.InvariantViolationException якщо генерація порушила інваріант
     */
    public MapView newWorld(long seed, int players, NpcShare npcShare) {
        WorldSizeInput input = WorldSizeInput.of(players, npcShare);
        ContentPack pack = content();
        StartWorld world = WorldGenerator.generate(Rng.of(seed), pack, input);
        WorldState state = WorldStates.of(seed, pack, world);
        return MapViews.of(state);
    }

    private synchronized ContentPack content() {
        if (content == null) {
            content = source.get();
        }
        return content;
    }
}
