package kolo.client.app;

import kolo.client.map.MapLayers;
import kolo.client.net.GameStart;

/** Перехід між екранами клієнта. Викликається лише з потоку JavaFX. */
public interface Navigator {

    void showMainMenu();

    void showNewWorld();

    void showConnect();

    /** Збережені світи вбудованого сервера. */
    void showLoad();

    /** Лобі поточної сесії. */
    void showLobby();

    /**
     * @param start світ, у який клієнт увійшов: рік і фаза
     */
    void showMap(MapLayers layers, GameStart start);

    /**
     * Hot-seat: карту сховано, комп'ютер передають наступному гравцеві.
     *
     * @param player номер гравця, якому передають комп'ютер
     */
    void showHandoff(int player, String nickname);

    void exit();
}
