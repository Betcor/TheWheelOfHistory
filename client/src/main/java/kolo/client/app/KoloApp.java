package kolo.client.app;

import atlantafx.base.theme.PrimerLight;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import kolo.client.i18n.Texts;
import kolo.client.map.MapLayers;
import kolo.client.net.EmbeddedGame;
import kolo.client.screen.MainMenuScreen;
import kolo.client.screen.MapScreen;
import kolo.client.screen.NewWorldScreen;

/**
 * JavaFX-застосунок клієнта: головне меню → параметри нового світу → карта з роками.
 *
 * <p>Світ живе на вбудованому сервері ({@link EmbeddedGame}): клієнт говорить із ним повідомленнями протоколу. Важка
 * робота (очікування сервера, растеризація карти) — в одному фоновому потоці, UI змінюється лише в потоці JavaFX.
 */
public final class KoloApp extends Application implements Navigator {
    private static final double INITIAL_WIDTH = 1280;
    private static final double INITIAL_HEIGHT = 800;

    private final Texts texts = Texts.ukrainian();
    private final EmbeddedGame game = EmbeddedGame.start();
    private final ExecutorService background = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "kolo-background");
        thread.setDaemon(true);
        return thread;
    });
    private final StackPane root = new StackPane();
    private Stage stage;

    public static void main(String[] args) {
        launch(KoloApp.class, args);
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        stage.setTitle(texts.text("app.title"));
        stage.setScene(new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT));
        showMainMenu();
        stage.show();
    }

    @Override
    public void stop() {
        background.shutdownNow();
        game.close();
    }

    @Override
    public void showMainMenu() {
        show(MainMenuScreen.create(this, texts));
    }

    @Override
    public void showNewWorld() {
        show(NewWorldScreen.create(this, texts, game, background));
    }

    @Override
    public void showMap(MapLayers layers, int turn) {
        show(MapScreen.create(this, texts, layers, turn, game, background));
    }

    @Override
    public void exit() {
        Platform.exit();
    }

    private void show(Parent screen) {
        root.getChildren().setAll(screen);
    }
}
