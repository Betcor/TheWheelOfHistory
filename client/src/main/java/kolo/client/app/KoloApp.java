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
import kolo.client.net.GameClient;
import kolo.client.net.GameStart;
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
import kolo.protocol.message.YearPhase;

/**
 * JavaFX-застосунок клієнта: головне меню → новий світ, збережений світ або підключення → лобі → карта з роками.
 *
 * <p>Світ живе на сервері — вбудованому ({@link GameClient}: одиночна гра й LAN-хост) чи віддаленому: клієнт говорить
 * із ним повідомленнями протоколу. Коли гра почалася (чи клієнт повернувся в неї), застосунок готує шари карти у
 * фоновому потоці й показує карту, а в новому світі спершу — генерацію держави гравця. UI змінюється лише в потоці JavaFX.
 */
public final class KoloApp extends Application implements Navigator {
    private static final double INITIAL_WIDTH = 1280;
    private static final double INITIAL_HEIGHT = 800;

    private final Texts texts = Texts.ukrainian();
    private final ExecutorService background = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "kolo-background");
        thread.setDaemon(true);
        return thread;
    });
    private final SessionModel session = new SessionModel(Platform::runLater);
    private final GameClient game = GameClient.start(session, background);
    private final ScreenContext context = new ScreenContext(this, texts, game, session, background);
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
        session.setOnGameStarted(this::gameStarted);
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
        show(NewWorldScreen.create(context));
    }

    @Override
    public void showConnect() {
        show(ConnectScreen.create(context));
    }

    @Override
    public void showLoad() {
        show(LoadScreen.create(context));
    }

    @Override
    public void showLobby() {
        show(LobbyScreen.create(context));
    }

    @Override
    public void showMap(MapLayers layers, GameStart start) {
        show(MapScreen.create(context, layers, start));
    }

    @Override
    public void showGeneration(MapLayers layers, GameStart start) {
        show(GenerationScreen.create(context, layers, start));
    }

    @Override
    public void showHandoff(int player, String nickname) {
        show(HandoffScreen.create(context, player, nickname));
    }

    @Override
    public void exit() {
        Platform.exit();
    }

    private void gameStarted(GameStart start) {
        background.execute(() -> {
            MapLayers layers = MapLayers.build(start.map());
            // Контент підписує картку держави й колеса; завантажується раз — тут, не в потоці UI.
            game.content();
            Platform.runLater(() -> {
                if (start.phase().phase() == YearPhase.GENERATION) {
                    showGeneration(layers, start);
                } else {
                    showMap(layers, start);
                }
            });
        });
    }

    private void show(Parent screen) {
        root.getChildren().setAll(screen);
    }
}
