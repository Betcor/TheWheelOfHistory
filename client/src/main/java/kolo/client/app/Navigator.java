package kolo.client.app;

import kolo.client.map.MapLayers;

/** Перехід між екранами клієнта. Викликається лише з потоку JavaFX. */
public interface Navigator {

    void showMainMenu();

    void showNewWorld();

    /**
     * @param turn поточний рік світу (хід)
     */
    void showMap(MapLayers layers, int turn);

    void exit();
}
