package kolo.client.screen;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.OptionalLong;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import kolo.client.i18n.Texts;
import kolo.engine.state.NpcShare;
import kolo.protocol.Protocol;
import kolo.protocol.message.Nicknames;

/**
 * Параметри нового світу (GD §3.4): нікнейм хоста, seed, частка NPC і чи відкрити гру для локальної мережі. Створює
 * лобі на вбудованому сервері; гравці — ті, хто буде в лобі, коли хост почне гру.
 */
public final class NewWorldScreen {

    private NewWorldScreen() {}

    public static Parent create(ScreenContext context) {
        Texts texts = context.texts();
        TextField nickname = new TextField(texts.text("new_world.nickname_default"));
        TextField seed = new TextField();
        seed.setPromptText(texts.text("new_world.seed_random"));
        ComboBox<NpcShare> npc = new ComboBox<>();
        npc.getItems().setAll(NpcShare.values());
        npc.getSelectionModel().select(NpcShare.NORMAL);
        npc.setCellFactory(list -> new ShareCell(texts));
        npc.setButtonCell(new ShareCell(texts));
        CheckBox lan = new CheckBox(texts.text("new_world.lan"));

        GridPane form = new GridPane(12, 10);
        form.addRow(0, new Label(texts.text("new_world.nickname")), nickname);
        form.addRow(1, new Label(texts.text("new_world.seed")), seed);
        form.addRow(2, new Label(texts.text("new_world.npc")), npc);
        form.add(lan, 1, 3);

        Label error = new Label();
        error.getStyleClass().add("danger");
        error.setWrapText(true);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(28, 28);

        Button create = new Button(texts.text("new_world.create"));
        create.setDefaultButton(true);
        Button back = new Button(texts.text("new_world.back"));
        back.setCancelButton(true);
        back.setOnAction(event -> context.navigator().showMainMenu());

        create.setOnAction(event -> {
            Optional<String> host = NicknameInput.parse(nickname.getText());
            if (host.isEmpty()) {
                error.setText(texts.text("new_world.nickname_invalid", Nicknames.MAX_LENGTH));
                return;
            }
            OptionalLong parsed;
            try {
                parsed = SeedInput.parse(seed.getText());
            } catch (NumberFormatException e) {
                error.setText(texts.text("new_world.seed_invalid"));
                return;
            }
            long chosen = parsed.orElseGet(() -> new SecureRandom().nextLong());
            error.setText("");
            context.session().reset();
            create.setDisable(true);
            back.setDisable(true);
            progress.setVisible(true);
            UiFutures.onUi(
                    context.game().hostLobby(host.get(), chosen, npc.getValue(), lan.isSelected()),
                    texts,
                    joined -> context.navigator().showLobby(),
                    message -> {
                        error.setText(message);
                        create.setDisable(false);
                        back.setDisable(false);
                        progress.setVisible(false);
                    });
        });

        HBox buttons = new HBox(10, back, create, progress);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(texts.text("new_world.title"));
        title.getStyleClass().add("title-2");
        Label port = new Label(texts.text("lobby.lan", Protocol.DEFAULT_PORT));
        port.getStyleClass().add("text-muted");
        port.visibleProperty().bind(lan.selectedProperty());
        VBox box = new VBox(18, title, form, port, buttons, error);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(24));
        box.setMaxWidth(520);
        VBox outer = new VBox(box);
        outer.setAlignment(Pos.CENTER);
        return outer;
    }

    private static final class ShareCell extends ListCell<NpcShare> {
        private final Texts texts;

        ShareCell(Texts texts) {
            this.texts = texts;
        }

        @Override
        protected void updateItem(NpcShare item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : texts.text("npc_share." + item.key()));
        }
    }
}
