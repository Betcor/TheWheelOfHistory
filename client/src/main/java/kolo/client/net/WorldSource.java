package kolo.client.net;

import kolo.engine.state.NpcShare;

/** Світ на сервері для екранів клієнта. Блокує: викликати лише з фонового потоку. */
public interface WorldSource {

    /**
     * Створює світ на сервері й повертає його карту, коли сервер почав приймати накази першого року.
     *
     * @throws ServerErrorException якщо сервер відповів помилкою
     * @throws ConnectionClosedException якщо з'єднання з сервером немає
     * @throws kolo.engine.error.GameException якщо відповідь пошкоджена чи версії не збіглися
     */
    GameStart newWorld(long seed, int players, NpcShare npcShare);

    /**
     * Закінчує накази року («Готово») і чекає, доки сервер розв'яже рік і почне приймати накази наступного.
     *
     * @param turn рік, накази якого закінчено
     * @return новий поточний рік
     * @throws ServerErrorException якщо сервер відповів помилкою (рік уже закрито, рік не вдалося розв'язати)
     * @throws ConnectionClosedException якщо з'єднання з сервером немає
     */
    int endYear(int turn);
}
