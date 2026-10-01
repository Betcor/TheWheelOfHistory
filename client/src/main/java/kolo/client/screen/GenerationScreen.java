package kolo.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import kolo.client.generation.AnimationMode;
import kolo.client.generation.CountryCardSections;
import kolo.client.generation.CountryCardView;
import kolo.client.generation.GenerationLabels;
import kolo.client.generation.GenerationPlan;
import kolo.client.generation.GenerationProgress;
import kolo.client.generation.WheelView;
import kolo.client.i18n.Texts;
import kolo.client.map.MapLayers;
import kolo.client.net.GameClient;
import kolo.client.net.GameStart;
import kolo.engine.state.LocalizedName;
import kolo.engine.view.CountryView;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.protocol.message.PlayerInfo;

/**
 * Генерація держави гравця (GD §4.12): вісім етапів-екранів, ключові колеса — з анімацією, службові — одразу
 * результатом, з шансами за кліком; наприкінці — картка держави й «Готово». Колеса лише показують результат з картки,
 * яку надіслав сервер. Перший рік починається, коли «Готово» натиснули всі гравці (ADR 0053).
 */
public final class GenerationScreen {

    /** Скільки крутиться колесо. */
    static final Duration SPIN = Duration.seconds(3.2);

    /** Пауза після зупинки коліс кроку — встигнути прочитати результат. */
    static final Duration PAUSE = Duration.seconds(0.8);

    private static final double WHEEL_SIZE = 230;
    private static final double SERVICE_WIDTH = 260;

    private final ScreenContext context;
    private final Texts texts;
    private final MapLayers layers;
    private final GameStart start;
    private final GenerationLabels labels;
    private final GenerationProgress progress;
    private AnimationMode mode = AnimationMode.KEY;

    private final Label stageTitle = new Label();
    private final BorderPane root = new BorderPane();
    private final Button skip;
    private final Button next;
    private final Button showAll;
    private final Button again;
    private final Button ready;
    /** Картки коліс поточного етапу. */
    private final Map<RollRecord, Shown> shown = new LinkedHashMap<>();
    /** Скільки коліс кроку ще крутиться. */
    private int spinning;

    private PauseTransition pause;
    private boolean alive = true;

    private GenerationScreen(ScreenContext context, MapLayers layers, GameStart start) {
        this.context = context;
        this.texts = context.texts();
        this.layers = layers;
        this.start = start;
        this.labels =
                new GenerationLabels(context.game().content(), texts, start.card(), number -> name(layers, number));
        this.progress = new GenerationProgress(GenerationPlan.of(start.card().rolls()));
        this.skip = new Button(texts.text("generation.skip"));
        this.next = new Button(texts.text("generation.next"));
        this.showAll = new Button(texts.text("generation.show_all"));
        this.again = new Button(texts.text("generation.again"));
        this.ready = new Button(texts.text("map.end_year"));
    }

    /**
     * @param layers шари карти — для переходу на карту після «Готово»
     * @param start вхід у гру у фазі генерації: картка держави гравця
     */
    public static Parent create(ScreenContext context, MapLayers layers, GameStart start) {
        return new GenerationScreen(context, layers, start).build();
    }

    private Parent build() {
        Label title = new Label(texts.text("generation.title"));
        title.getStyleClass().add("title-3");
        stageTitle.getStyleClass().add("title-4");
        ChoiceBox<AnimationMode> modes = new ChoiceBox<>();
        modes.getItems().setAll(AnimationMode.values());
        modes.setValue(mode);
        modes.setConverter(new StringConverter<>() {
            @Override
            public String toString(AnimationMode value) {
                return value == null ? "" : texts.text("generation.mode." + value.key());
            }

            @Override
            public AnimationMode fromString(String text) {
                throw new UnsupportedOperationException("лише вибір зі списку");
            }
        });
        modes.setOnAction(event -> changeMode(modes.getValue()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button menu = new Button(texts.text("map.main_menu"));
        menu.setOnAction(event -> {
            close();
            context.game().leave();
            context.session().reset();
            context.navigator().showMainMenu();
        });
        ToolBar toolbar = new ToolBar(
                title, new Separator(), stageTitle, spacer, new Label(texts.text("generation.mode")), modes, menu);

        skip.setOnAction(event -> skip());
        next.setOnAction(event -> {
            progress.nextStage();
            show();
        });
        showAll.setOnAction(event -> {
            stopAll();
            progress.showAll();
            show();
        });
        again.setOnAction(event -> {
            progress.restart();
            show();
        });
        ready.setDefaultButton(true);
        ready.setOnAction(event -> ready());
        HBox buttons = new HBox(10, again, showAll, skip, next, ready);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 14, 10, 14));

        root.setTop(toolbar);
        root.setBottom(buttons);
        root.sceneProperty().addListener((property, old, scene) -> {
            if (scene == null) {
                close();
            }
        });
        show();
        return root;
    }

    /** Показує поточний етап (і починає його кроки) або картку. */
    private void show() {
        stopAll();
        shown.clear();
        boolean card = progress.onCard();
        skip.setVisible(!card);
        skip.setManaged(!card);
        next.setVisible(!card);
        next.setManaged(!card);
        showAll.setVisible(!card);
        showAll.setManaged(!card);
        again.setVisible(card);
        again.setManaged(card);
        ready.setVisible(card);
        ready.setManaged(card);
        if (card) {
            stageTitle.setText(texts.text("generation.card"));
            boolean meReady = context.session().players().get().stream()
                    .anyMatch(p -> context.game().isMe(p) && p.ready());
            ready.setText(texts.text(meReady ? "generation.to_map" : "map.end_year"));
            root.setCenter(scroll(CountryCardView.create(
                    labels.card().name().fullName().nominative(),
                    CountryCardSections.of(labels, texts, start.turn()))));
            return;
        }
        GenerationPlan.Stage stage = progress.stage();
        stageTitle.setText(texts.text(
                "generation.stage",
                progress.stageIndex() + 1,
                progress.plan().stages().size(),
                texts.text("generation.stage." + stage.kind().key())));
        FlowPane wheels = new FlowPane(18, 18);
        wheels.setPadding(new Insets(18));
        wheels.setAlignment(Pos.TOP_CENTER);
        for (RollRecord roll : stage.rolls()) {
            Shown item = roll(roll);
            shown.put(roll, item);
            wheels.getChildren().add(item.node());
        }
        root.setCenter(scroll(wheels));
        next.setDisable(true);
        skip.setDisable(false);
        runStep();
    }

    /** Наступний крок етапу: колеса крутяться разом, службові — одразу результатом. */
    private void runStep() {
        if (!alive) {
            return;
        }
        Optional<GenerationPlan.Step> step = progress.nextStep();
        if (step.isEmpty()) {
            stageShown();
            return;
        }
        List<WheelView> spins = new ArrayList<>();
        for (RollRecord roll : step.get().rolls()) {
            Shown card = shown.get(roll);
            if (card.wheel() != null && mode.animates(roll.kind())) {
                spins.add(card.wheel());
            } else {
                card.reveal();
            }
        }
        if (spins.isEmpty()) {
            runStep();
            return;
        }
        spinning = spins.size();
        for (WheelView wheel : spins) {
            wheel.spin(SPIN, () -> {
                if (--spinning == 0 && alive) {
                    pause = new PauseTransition(PAUSE);
                    pause.setOnFinished(event -> runStep());
                    pause.play();
                }
            });
        }
    }

    private void stageShown() {
        skip.setDisable(true);
        next.setDisable(false);
        next.requestFocus();
    }

    /** «Пропустити анімацію»: решта етапу — одразу. */
    private void skip() {
        // Колеса поточного кроку, що ще крутяться, — одразу до результату; потім решта етапу.
        shown.values().forEach(Shown::revealIfSpinning);
        stopAll();
        for (GenerationPlan.Step step : progress.skipStage()) {
            step.rolls().forEach(roll -> shown.get(roll).reveal());
        }
        stageShown();
    }

    private void changeMode(AnimationMode chosen) {
        mode = chosen;
        if (mode == AnimationMode.NONE && !progress.onCard() && !progress.stageDone()) {
            skip();
        }
    }

    private void ready() {
        GameClient game = context.game();
        boolean meReady = context.session().players().get().stream().anyMatch(p -> game.isMe(p) && p.ready());
        close();
        if (!meReady) {
            game.ready(start.turn());
            OptionalInt next = game.handoffAfterReady();
            if (next.isPresent()) {
                String nickname = context.session().players().get().stream()
                        .filter(p -> p.number() == next.getAsInt())
                        .map(PlayerInfo::nickname)
                        .findFirst()
                        .orElse("");
                context.navigator().showHandoff(next.getAsInt(), nickname);
                return;
            }
        }
        context.navigator().showMap(layers, start);
    }

    private Shown roll(RollRecord roll) {
        boolean wheel = GenerationPlan.isKey(roll.kind()) || mode == AnimationMode.ALL;
        if (wheel) {
            WheelView view = new WheelView(roll, labels, WHEEL_SIZE);
            return new Shown(view, view, null);
        }
        Label name = new Label(labels.wheel(roll.kind()));
        name.getStyleClass().add("text-muted");
        Label result = new Label("?");
        result.getStyleClass().add("text-bold");
        result.setWrapText(true);
        TitledPane chances = new TitledPane(texts.text("generation.chances"), chances(roll));
        chances.setExpanded(false);
        chances.setAnimated(false);
        VBox box = new VBox(4, name, result, chances);
        box.setPrefWidth(SERVICE_WIDTH);
        box.setMaxWidth(SERVICE_WIDTH);
        return new Shown(box, null, () -> result.setText(labels.result(roll)));
    }

    private Node chances(RollRecord roll) {
        VBox lines = new VBox(2);
        if (roll.advantage() != 0) {
            Label advantage = new Label(texts.text("generation.advantage", signed(roll.advantage())));
            advantage.getStyleClass().add("text-muted");
            lines.getChildren().add(advantage);
        }
        for (RolledSector sector : roll.sectors()) {
            Label line = new Label(
                    texts.text("generation.chance", labels.sector(roll, sector.id()), percent(sector.weightBp())));
            line.setWrapText(true);
            if (sector.id().equals(roll.resultSectorId())) {
                line.getStyleClass().add("text-bold");
            }
            lines.getChildren().add(line);
        }
        return lines;
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    /** Шанс з bp: «24,5%». */
    private static String percent(int bp) {
        int tenths = (bp + 5) / 10;
        return tenths / 10 + "," + tenths % 10 + "%";
    }

    private static ScrollPane scroll(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        return pane;
    }

    private static Optional<LocalizedName> name(MapLayers layers, int number) {
        return layers.view().countries().stream()
                .filter(country -> country.number() == number)
                .map(CountryView::name)
                .findFirst();
    }

    private void stopAll() {
        if (pause != null) {
            pause.stop();
            pause = null;
        }
        spinning = 0;
        shown.values().forEach(Shown::stop);
    }

    private void close() {
        alive = false;
        stopAll();
    }

    /**
     * Колесо на екрані етапу: з анімацією ({@code wheel}) чи службове з результатом ({@code showResult}).
     */
    private record Shown(Node node, WheelView wheel, Runnable showResult) {

        void reveal() {
            if (wheel != null) {
                wheel.showResult();
            } else {
                showResult.run();
            }
        }

        /** Колесо, що крутиться, — одразу до результату. */
        void revealIfSpinning() {
            if (wheel != null && wheel.spinning()) {
                wheel.showResult();
            }
        }

        void stop() {
            if (wheel != null) {
                wheel.stop();
            }
        }
    }
}
