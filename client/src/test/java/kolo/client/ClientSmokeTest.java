package kolo.client;

import static kolo.client.TestWorlds.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kolo.client.i18n.Texts;
import kolo.client.map.MapLayers;
import kolo.client.map.MapMode;
import kolo.client.net.GameClient;
import kolo.client.net.GameStart;
import kolo.client.net.LanPorts;
import kolo.client.net.RecordingListener;
import kolo.client.screen.ProvinceDescription;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Смок без вікна: те, що клієнт робить між кнопкою «Створити лобі» і першим кадром карти, — вбудований сервер і
 * з'єднання з ним через {@code LocalChannel}, лобі, «Почати гру», шари карти, кольори кожного режиму й тексти про кожну
 * провінцію — і кнопка «Готово», що переводить світ у наступний рік.
 */
class ClientSmokeTest {

    @TempDir
    Path worlds;

    @Test
    @Timeout(60)
    void newLobbyToMapLayers() throws Exception {
        Texts texts = Texts.ukrainian();
        RecordingListener events = new RecordingListener();
        ExecutorService background = Executors.newSingleThreadExecutor();
        MapView view;
        try (GameClient game = GameClient.start(worlds, LanPorts.ANY, events, background)) {
            await(game.hostLobby(texts.text("new_world.nickname_default"), 42, NpcShare.NORMAL, false));
            game.startGame();
            GameStart start = TestWorlds.started(events);
            view = start.map();
            // Генерацію переглянуто — рік 0.
            game.ready(start.turn());
            events.awaitOrders(start.turn());
            game.ready(start.turn());
            events.awaitOrders(start.turn() + 1);
        } finally {
            background.shutdownNow();
        }

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
