package kolo.client.app;

import atlantafx.base.theme.PrimerLight;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** JavaFX-застосунок клієнта. Поки що — порожнє вікно з темою AtlantaFX. */
public final class KoloApp extends Application {
    private static final double INITIAL_WIDTH = 1280;
    private static final double INITIAL_HEIGHT = 800;

    public static void main(String[] args) {
        launch(KoloApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages", Locale.of("uk"));

        stage.setTitle(messages.getString("app.title"));
        stage.setScene(new Scene(new StackPane(), INITIAL_WIDTH, INITIAL_HEIGHT));
        stage.show();
    }
}
