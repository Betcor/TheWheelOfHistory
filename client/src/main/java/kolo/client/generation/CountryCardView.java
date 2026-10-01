package kolo.client.generation;

import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Картка держави (GD §4.12): розділи {@link CountryCardSections} — заголовок і рядки «назва — значення». */
public final class CountryCardView {

    private static final double LABEL_WIDTH = 200;

    private CountryCardView() {}

    public static Node create(String title, List<CountryCardSections.Section> sections) {
        Label heading = new Label(title);
        heading.getStyleClass().add("title-2");
        heading.setWrapText(true);
        VBox box = new VBox(14, heading);
        box.setPadding(new Insets(16));
        box.setMaxWidth(860);
        for (CountryCardSections.Section section : sections) {
            if (section.lines().isEmpty()) {
                continue;
            }
            Label name = new Label(section.title());
            name.getStyleClass().add("title-4");
            GridPane grid = new GridPane();
            grid.setHgap(14);
            grid.setVgap(4);
            ColumnConstraints labels = new ColumnConstraints(LABEL_WIDTH);
            ColumnConstraints values = new ColumnConstraints();
            values.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(labels, values);
            int row = 0;
            for (CountryCardSections.Line line : section.lines()) {
                Label label = new Label(line.label());
                label.getStyleClass().add("text-muted");
                label.setWrapText(true);
                Label value = new Label(line.value());
                value.setWrapText(true);
                GridPane.setValignment(label, VPos.TOP);
                grid.addRow(row++, label, value);
            }
            box.getChildren().addAll(name, grid);
        }
        return box;
    }
}
