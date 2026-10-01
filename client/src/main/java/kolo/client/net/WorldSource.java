package kolo.client.net;

import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;

/** Звідки екран нового світу бере карту. Блокує: викликати лише з фонового потоку. */
public interface WorldSource {

    /**
     * Створює світ на сервері й повертає його карту.
     *
     * @throws ServerErrorException якщо сервер відповів помилкою
     * @throws ConnectionClosedException якщо з'єднання з сервером немає
     * @throws kolo.engine.error.GameException якщо відповідь пошкоджена чи версії не збіглися
     */
    MapView newWorld(long seed, int players, NpcShare npcShare);
}
