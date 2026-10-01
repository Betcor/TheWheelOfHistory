package kolo.client.screen;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import kolo.client.i18n.Texts;

/**
 * Передача комп'ютера в hot-seat (GD §21): карту сховано, доки наступний гравець не сяде за комп'ютер, — інші не
 * бачать його держави й наказів. Його карту покаже застосунок, коли гра клієнта перемкнеться на його місце.
 */
public final class HandoffScreen {

    private HandoffScreen() {}

    /**
     * @param player номер гравця, якому передають комп'ютер
     * @param nickname його нікнейм
     */
    public static Parent create(ScreenContext context, int player, String nickname) {
        Texts texts = context.texts();
        Label title = new Label(texts.text("handoff.title", nickname));
        title.getStyleClass().add("title-2");
        title.setWrapText(true);
        Label hint = new Label(texts.text("handoff.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("danger");
        error.setWrapText(true);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(28, 28);

        Button take = new Button(texts.text("handoff.take", nickname));
        take.setDefaultButton(true);
        take.setOnAction(event -> {
            take.setDisable(true);
            progress.setVisible(true);
            if (!context.game().showPlayer(player)) {
                // Гравця вже немає за цим комп'ютером (сесію полишили).
                progress.setVisible(false);
                error.setText(texts.text("handoff.gone", nickname));
            }
        });
        Button menu = new Button(texts.text("map.main_menu"));
        menu.setOnAction(event -> {
            context.game().leave();
            context.session().reset();
            context.navigator().showMainMenu();
        });

        HBox buttons = new HBox(10, menu, take, progress);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(18, title, hint, buttons, error);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(24));
        box.setMaxWidth(520);
        VBox outer = new VBox(box);
        outer.setAlignment(Pos.CENTER);
        return outer;
    }
}
