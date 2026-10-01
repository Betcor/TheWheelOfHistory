package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import kolo.client.TestWorlds;
import kolo.client.app.Navigator;
import kolo.client.generation.WheelView;
import kolo.client.i18n.Texts;
import kolo.client.net.GameClient;
import kolo.client.net.GameStart;
import kolo.client.net.LanPorts;
import kolo.client.net.TestCards;
import kolo.client.screen.ConnectScreen;
import kolo.client.screen.GenerationScreen;
import kolo.client.screen.HandoffScreen;
import kolo.client.screen.LoadScreen;
import kolo.client.screen.LobbyScreen;
import kolo.client.screen.MainMenuScreen;
import kolo.client.screen.MapScreen;
import kolo.client.screen.NewWorldScreen;
import kolo.client.screen.ScreenContext;
import kolo.client.state.SessionModel;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Смок із JavaFX: екрани будуються, карта малює кадри растром і векторно. Вікно не показується — сцена рендериться в
 * знімок. Потрібен дисплей (у CI — Xvfb); без нього тест пропускається.
 */
class MapScreenSmokeTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final long TIMEOUT_SECONDS = 20;

    private static final Navigator NAVIGATOR = new Navigator() {
        @Override
        public void showMainMenu() {}

        @Override
        public void showNewWorld() {}

        @Override
        public void showConnect() {}

        @Override
        public void showLoad() {}

        @Override
        public void showLobby() {}

        @Override
        public void showMap(MapLayers layers, GameStart start) {}

        @Override
        public void showGeneration(MapLayers layers, GameStart start) {}

        @Override
        public void showHandoff(int player, String nickname) {}

        @Override
        public void exit() {}
    };

    private static final Executor BACKGROUND = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "smoke-background");
        thread.setDaemon(true);
        return thread;
    });

    @TempDir
    static Path worlds;

    private static GameClient game;
    private static SessionModel session;

    @AfterAll
    static void stopGame() {
        if (game != null) {
            game.close();
        }
    }

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        } catch (UnsupportedOperationException | UnsatisfiedLinkError noDisplay) {
            Assumptions.abort("JavaFX без дисплея: " + noDisplay.getMessage());
        }
        assertThat(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        Platform.setImplicitExit(false);
        session = new SessionModel(Platform::runLater);
        game = GameClient.start(worlds, LanPorts.ANY, session, BACKGROUND);
    }

    private static ScreenContext context() {
        return new ScreenContext(NAVIGATOR, Texts.ukrainian(), game, session, Runnable::run);
    }

    @Test
    void screensBuild() throws Exception {
        Texts texts = Texts.ukrainian();
        onFx(() -> {
            new Scene(MainMenuScreen.create(NAVIGATOR, texts), WIDTH, HEIGHT);
            new Scene(NewWorldScreen.create(context()), WIDTH, HEIGHT);
            new Scene(ConnectScreen.create(context()), WIDTH, HEIGHT);
            new Scene(LobbyScreen.create(context()), WIDTH, HEIGHT);
            new Scene(LoadScreen.create(context()), WIDTH, HEIGHT);
            new Scene(HandoffScreen.create(context(), 2, "Ігор"), WIDTH, HEIGHT);
            return null;
        });
    }

    @Test
    void savedLobbyShowsSeatsToTheHost() throws Exception {
        String world = "0123456789abcdef0123456789abcdef";
        // Лобі завантаженого світу, яке бачить гість-хост: місця гравців світу й він сам без держави.
        session.lobby(new ServerMessage.Lobby(
                1,
                world,
                new LobbySetup.SavedWorld("world-5", 5, 3, TurnTimer.MANUAL),
                List.of(
                        new PlayerInfo(1, "Оля", false, false, false, OptionalInt.of(0)),
                        new PlayerInfo(2, "Марко", true, true, false, OptionalInt.empty())),
                List.of(TurnTimer.MANUAL)));
        Parent screen = onFx(() -> {
            Parent lobby = LobbyScreen.create(context());
            new Scene(lobby, WIDTH, HEIGHT);
            lobby.applyCss();
            lobby.layout();
            return lobby;
        });

        assertThat(onFx(() -> screen.lookupAll(".label").stream()
                        .map(node -> ((Label) node).getText())
                        .toList()))
                .contains("Світ «world-5» · seed 5 · продовження з 1973 року · таймер: ручний");
        onFx(() -> {
            session.reset();
            return null;
        });
    }

    @Test
    void generationShowsStagesAndTheCountryCard() throws Exception {
        Texts texts = Texts.ukrainian();
        GameStart start = TestWorlds.start(1970, 1, NpcShare.NORMAL);
        MapLayers layers = MapLayers.build(start.map());
        Parent screen = onFx(() -> {
            Parent generation = GenerationScreen.create(context(), layers, start);
            new Scene(generation, WIDTH, HEIGHT);
            generation.applyCss();
            generation.layout();
            return generation;
        });

        // Перший етап — «Земля»: ключові колеса крутяться, «Пропустити» одразу показує їхній результат.
        assertThat(labels(screen)).anyMatch(text -> text.contains(texts.text("generation.stage.land")));
        onFx(() -> {
            button(screen, texts.text("generation.skip")).fire();
            return null;
        });
        assertThat(onFx(() -> screen.lookupAll(".label").stream()
                        .filter(node -> node.getParent() instanceof WheelView)
                        .map(node -> ((Label) node).getText())
                        .toList()))
                .isNotEmpty()
                .doesNotContain("?");
        onFx(() -> {
            button(screen, texts.text("generation.next")).fire();
            return null;
        });
        assertThat(labels(screen)).anyMatch(text -> text.contains(texts.text("generation.stage.regime")));

        onFx(() -> {
            button(screen, texts.text("generation.show_all")).fire();
            screen.applyCss();
            screen.layout();
            return null;
        });
        assertThat(labels(screen))
                .contains(
                        texts.text("card.section.state"),
                        start.card().name().fullName().nominative());
        onFx(() -> {
            // Екран прибрано — анімації зупинено.
            screen.getScene().setRoot(new javafx.scene.layout.Pane());
            return null;
        });
    }

    private static List<String> labels(Parent screen) throws Exception {
        return onFx(() -> {
            screen.applyCss();
            screen.layout();
            return screen.lookupAll(".label").stream()
                    .map(node -> ((Label) node).getText())
                    .toList();
        });
    }

    private static javafx.scene.control.Button button(Parent screen, String text) {
        return screen.lookupAll(".button").stream()
                .filter(node -> node instanceof javafx.scene.control.Button b
                        && b.getText().equals(text))
                .map(node -> (javafx.scene.control.Button) node)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void mapDrawsRasterAndVectorFrames() throws Exception {
        MapLayers layers = MapLayers.build(TestWorlds.DEFAULT);
        Texts texts = Texts.ukrainian();
        // Растеризація режиму — одразу в потоці виклику: плитки з'являються наступним runLater.
        Parent screen = onFx(() -> MapScreen.create(
                context(),
                layers,
                new GameStart(TestWorlds.DEFAULT, TestCards.CARD, new ServerMessage.Phase(0, YearPhase.ORDERS))));
        Scene scene = onFx(() -> {
            Scene result = new Scene(screen, WIDTH, HEIGHT);
            screen.applyCss();
            screen.layout();
            return result;
        });
        MapCanvas canvas = onFx(() -> (MapCanvas)
                ((StackPane) ((BorderPane) screen).getCenter()).getChildren().getFirst());

        waitUntil(() -> onFxQuietly(() -> canvas.rasterReady() && canvas.frames() > 0));
        int rasterFrames = onFx(canvas::frames);
        WritableImage raster = onFx(() -> scene.snapshot(null));
        assertThat(colors(raster)).contains(MapPalette.SEA).hasSizeGreaterThan(10);

        onFx(() -> {
            MapCamera camera = canvas.camera();
            camera.zoom(camera.maxScale() / camera.scale(), canvas.getWidth() / 2, canvas.getHeight() / 2);
            canvas.fit();
            camera.zoom(camera.maxScale() / camera.scale(), canvas.getWidth() / 2, canvas.getHeight() / 2);
            canvas.setMode(MapMode.TERRAIN);
            return null;
        });
        assertThat(onFx(() -> canvas.camera().scale())).isGreaterThanOrEqualTo(layers.vectorScale());
        waitUntil(() -> onFxQuietly(() -> canvas.frames() > rasterFrames));
        WritableImage vector = onFx(() -> scene.snapshot(null));
        assertThat(colors(vector)).hasSizeGreaterThan(3);

        // «Моя держава» — бічна панель картки поверх карти; Esc її закриває.
        onFx(() -> {
            button(screen, texts.text("map.my_country")).fire();
            return null;
        });
        assertThat(labels(screen)).contains(texts.text("card.section.state"));
        onFx(() -> {
            canvas.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
            return null;
        });
        // Кнопка «Закрити» — у заголовку панелі.
        assertThat(onFx(() -> button(screen, texts.text("map.card.close"))
                        .getParent()
                        .getParent()
                        .isVisible()))
                .isFalse();
        onFx(() -> {
            canvas.dispose();
            return null;
        });
    }

    private static Set<Integer> colors(WritableImage image) {
        PixelReader reader = image.getPixelReader();
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < (int) image.getHeight(); y += 5) {
            for (int x = 0; x < (int) image.getWidth(); x += 5) {
                colors.add(reader.getArgb(x, y));
            }
        }
        return colors;
    }

    private static <T> T onFx(Supplier<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.get());
            } catch (RuntimeException | Error e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static boolean onFxQuietly(Supplier<Boolean> condition) {
        try {
            return onFx(condition);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void waitUntil(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.get()) {
            assertThat(System.nanoTime()).as("timeout").isLessThan(deadline);
            Thread.sleep(20);
        }
    }
}
