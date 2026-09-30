package kolo.client;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.client.i18n.Texts;
import kolo.client.map.MapLayers;
import kolo.client.map.MapMode;
import kolo.client.screen.ProvinceDescription;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

/**
 * Смок без вікна: те, що клієнт робить між кнопкою «Згенерувати світ» і першим кадром карти, — вбудований сервер,
 * шари карти, кольори кожного режиму й тексти про кожну провінцію.
 */
class ClientSmokeTest {

    @Test
    void newWorldToMapLayers() {
        Texts texts = Texts.ukrainian();
        MapView view = EmbeddedServer.withBundledContent().newWorld(42, 1, NpcShare.NORMAL);

        MapLayers layers = MapLayers.build(view);
        for (MapMode mode : MapMode.values()) {
            assertThat(layers.paint(mode)).hasSameSizeAs(layers.levels());
        }
        for (int n = 0; n < view.cells().size(); n++) {
            assertThat(ProvinceDescription.title(view, n, texts)).isNotBlank();
            assertThat(ProvinceDescription.lines(view, n, texts)).isNotEmpty();
        }
        assertThat(texts.text("app.title")).isEqualTo("Колесо Історії");
    }
}
