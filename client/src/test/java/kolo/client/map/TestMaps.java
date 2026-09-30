package kolo.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.engine.generation.map.GridPoint;
import kolo.engine.state.Climate;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;
import kolo.engine.view.CellKind;
import kolo.engine.view.CellView;
import kolo.engine.view.CountryView;
import kolo.engine.view.MapView;

/**
 * Маленька карта з квадратів 10 × 10: 3 стовпці × 2 рядки, комірка {@code рядок × 3 + стовпець}, рядок 0 — внизу (вісь
 * {@code y} рушія — вгору, тож на екрані він нижній).
 *
 * <pre>
 *   рядок 1:  [3] держава 0   [4] нічийна     [5] озеро
 *   рядок 0:  [0] держава 0   [1] держава 1   [2] море
 * </pre>
 *
 * Через провінцію 1 тече річка в море (комірка 2).
 */
public final class TestMaps {

    public static final int SIDE = 10;
    public static final MapView MAP = map();

    private TestMaps() {}

    public static LocalizedName name(String full, String shortName) {
        return new LocalizedName(phrase(full), phrase(shortName));
    }

    private static NounPhrase phrase(String text) {
        return new NounPhrase(GrammaticalGender.FEMININE, List.of(text, text, text, text, text, text, text));
    }

    private static MapView map() {
        List<CellView> cells = new ArrayList<>();
        cells.add(land(0, 0, OptionalInt.of(0), Terrain.PLAIN, OptionalInt.empty()));
        cells.add(land(1, 0, OptionalInt.of(1), Terrain.FOREST, OptionalInt.of(2)));
        cells.add(water(2, 0, CellKind.SEA));
        cells.add(land(0, 1, OptionalInt.of(0), Terrain.MOUNTAINS, OptionalInt.empty()));
        cells.add(land(1, 1, OptionalInt.empty(), Terrain.DESERT, OptionalInt.empty()));
        cells.add(water(2, 1, CellKind.LAKE));
        List<CountryView> countries = List.of(
                new CountryView(0, name("Республіка Велор", "Велор"), true, 2),
                new CountryView(1, name("Королівство Арна", "Арна"), false, 1));
        return new MapView(1970, 3 * SIDE, 2 * SIDE, cells, countries);
    }

    private static CellView land(int column, int row, OptionalInt country, Terrain terrain, OptionalInt downstream) {
        Relief relief = terrain == Terrain.MOUNTAINS ? Relief.MOUNTAINS : Relief.PLAIN;
        return new CellView(
                site(column, row),
                square(column, row),
                neighbors(column, row),
                CellKind.LAND,
                Optional.of(terrain),
                Optional.of(relief),
                Optional.of(Climate.TEMPERATE),
                OptionalInt.of(terrain == Terrain.MOUNTAINS ? 80 : 20),
                OptionalInt.of(10 + 20 * column + 30 * row),
                downstream.isPresent(),
                downstream,
                country);
    }

    private static CellView water(int column, int row, CellKind kind) {
        return new CellView(
                site(column, row),
                square(column, row),
                neighbors(column, row),
                kind,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false,
                OptionalInt.empty(),
                OptionalInt.empty());
    }

    private static GridPoint site(int column, int row) {
        return new GridPoint(column * SIDE + SIDE / 2, row * SIDE + SIDE / 2);
    }

    private static List<GridPoint> square(int column, int row) {
        int x = column * SIDE;
        int y = row * SIDE;
        return List.of(
                new GridPoint(x, y),
                new GridPoint(x + SIDE, y),
                new GridPoint(x + SIDE, y + SIDE),
                new GridPoint(x, y + SIDE));
    }

    private static List<Integer> neighbors(int column, int row) {
        List<Integer> result = new ArrayList<>();
        if (row > 0) {
            result.add((row - 1) * 3 + column);
        }
        if (column > 0) {
            result.add(row * 3 + column - 1);
        }
        if (column < 2) {
            result.add(row * 3 + column + 1);
        }
        if (row < 1) {
            result.add((row + 1) * 3 + column);
        }
        return result;
    }
}
