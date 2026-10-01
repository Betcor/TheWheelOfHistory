package kolo.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import kolo.client.i18n.Texts;
import kolo.client.map.MapLayers;
import kolo.client.map.MapMode;
import kolo.client.net.EmbeddedGame;
import kolo.client.net.GameStart;
import kolo.client.screen.ProvinceDescription;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Смок без вікна: те, що клієнт робить між кнопкою «Згенерувати світ» і першим кадром карти, — вбудований сервер і
 * з'єднання з ним через {@code LocalChannel}, шари карти, кольори кожного режиму й тексти про кожну провінцію — і
 * кнопка «Готово», що переводить світ у наступний рік.
 */
class ClientSmokeTest {

    @TempDir
    Path worlds;

    @Test
    @Timeout(60)
    void newWorldToMapLayers() {
        Texts texts = Texts.ukrainian();
        MapView view;
        try (EmbeddedGame game = EmbeddedGame.start(worlds)) {
            GameStart start = game.newWorld(42, 1, NpcShare.NORMAL);
            view = start.map();
            assertThat(game.endYear(start.turn())).isEqualTo(start.turn() + 1);
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
