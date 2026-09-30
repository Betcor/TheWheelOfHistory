package kolo.client.screen;

import java.util.ArrayList;
import java.util.List;
import kolo.client.i18n.Texts;
import kolo.engine.view.CellKind;
import kolo.engine.view.CellView;
import kolo.engine.view.CountryView;
import kolo.engine.view.MapView;

/** Текст про комірку карти для панелі провінції й рядка стану. Без JavaFX — тестується без дисплея. */
public final class ProvinceDescription {

    private ProvinceDescription() {}

    /** Заголовок: провінція з номером, море чи озеро. */
    public static String title(MapView view, int cell, Texts texts) {
        CellView data = view.cells().get(cell);
        return switch (data.kind()) {
            case LAND -> texts.text("map.info.province", cell);
            case SEA -> texts.text("map.info.sea");
            case LAKE -> texts.text("map.info.lake");
        };
    }

    /** Хто володіє: повна назва держави, нічийна земля, для води — порожньо. */
    public static String owner(MapView view, int cell, Texts texts) {
        CellView data = view.cells().get(cell);
        if (!data.isLand()) {
            return "";
        }
        if (data.country().isEmpty()) {
            return texts.text("map.info.unclaimed");
        }
        CountryView country = view.countries().get(data.country().getAsInt());
        String name = country.name().fullName().nominative();
        return country.player() ? texts.text("map.info.player_country", name) : name;
    }

    /** Один рядок для рядка стану під картою. */
    public static String summary(MapView view, int cell, Texts texts) {
        String owner = owner(view, cell, texts);
        String title = title(view, cell, texts);
        return owner.isEmpty() ? title : texts.text("map.info.summary", title, owner);
    }

    /** Рядки панелі провінції: власник і все, що показують режими карти. Для води — лише вид водойми. */
    public static List<String> lines(MapView view, int cell, Texts texts) {
        CellView data = view.cells().get(cell);
        List<String> lines = new ArrayList<>();
        if (!data.isLand()) {
            lines.add(texts.text(data.kind() == CellKind.SEA ? "map.info.sea_hint" : "map.info.lake_hint"));
            return List.copyOf(lines);
        }
        lines.add(texts.text("map.info.owner", owner(view, cell, texts)));
        lines.add(texts.text(
                "map.info.terrain",
                texts.text("terrain." + data.terrain().orElseThrow().key())));
        lines.add(texts.text(
                "map.info.relief",
                texts.text("relief." + data.relief().orElseThrow().key()),
                data.height().orElseThrow()));
        lines.add(texts.text(
                "map.info.climate",
                texts.text("climate." + data.climate().orElseThrow().key())));
        lines.add(texts.text("map.info.fertility", data.fertility().orElseThrow()));
        lines.add(texts.text(data.river() ? "map.info.river" : "map.info.no_river"));
        boolean coastal =
                data.neighbors().stream().anyMatch(n -> view.cells().get(n).kind() == CellKind.SEA);
        if (coastal) {
            lines.add(texts.text("map.info.coast"));
        }
        return List.copyOf(lines);
    }
}
