package kolo.client.app;

import kolo.client.map.MapLayers;

/** Перехід між екранами клієнта. Викликається лише з потоку JavaFX. */
public interface Navigator {

    void showMainMenu();

    void showNewWorld();

    void showMap(MapLayers layers);

    void exit();
}
