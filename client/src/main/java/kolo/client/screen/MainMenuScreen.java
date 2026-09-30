package kolo.client.screen;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import kolo.client.app.Navigator;
import kolo.client.i18n.Texts;

/** Головне меню (GD §22.2). Завантаження, мережа й налаштування — неактивні, доки немає збережень і мережі. */
public final class MainMenuScreen {

    private static final double BUTTON_WIDTH = 260;

    private MainMenuScreen() {}

    public static Parent create(Navigator navigator, Texts texts) {
        Label title = new Label(texts.text("app.title"));
        title.getStyleClass().addAll("title-1");

        Button newGame = button(texts.text("menu.new_game"));
        newGame.setDefaultButton(true);
        newGame.setOnAction(event -> navigator.showNewWorld());
        Button load = unavailable(texts.text("menu.load"), texts);
        Button connect = unavailable(texts.text("menu.connect"), texts);
        Button settings = unavailable(texts.text("menu.settings"), texts);
        Button exit = button(texts.text("menu.exit"));
        exit.setOnAction(event -> navigator.exit());

        VBox box = new VBox(12, title, newGame, load, connect, settings, exit);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Button button(String text) {
        Button button = new Button(text);
        button.setPrefWidth(BUTTON_WIDTH);
        return button;
    }

    private static Button unavailable(String text, Texts texts) {
        Button button = button(text);
        button.setText(texts.text("menu.unavailable", text));
        button.setDisable(true);
        return button;
    }
}
