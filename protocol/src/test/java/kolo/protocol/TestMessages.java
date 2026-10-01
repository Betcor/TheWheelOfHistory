package kolo.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.engine.state.CellKind;
import kolo.engine.state.Climate;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.GridPoint;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;
import kolo.engine.view.CellView;
import kolo.engine.view.CountryView;
import kolo.engine.view.MapView;

/**
 * Карти для тестів протоколу: смуга квадратних комірок, у якій трапляються всі варіанти полів — суходіл з річкою й без,
 * нічийна земля, море й озеро, дві держави з українськими назвами (кирилиця й апостроф на дроті).
 */
public final class TestMessages {

    public static final String HASH = "a".repeat(64);

    /** Ключ світу. */
    public static final String WORLD = "0123456789abcdef0123456789abcdef";

    private static final int SIDE = 10;

    private TestMessages() {}

    /** Карта з {@code cells} комірок (щонайменше одна). */
    public static MapView map(long seed, int cells) {
        List<CellView> views = new ArrayList<>(cells);
        for (int n = 0; n < cells; n++) {
            views.add(cell(n, cells));
        }
        List<CountryView> countries = List.of(
                new CountryView(0, name("Республіка Вел'ор"), true, 1), new CountryView(1, name("Орін"), false, 1));
        return new MapView(seed, SIDE * cells, SIDE, views, countries);
    }

    public static LocalizedName name(String text) {
        NounPhrase phrase = new NounPhrase(
                GrammaticalGender.FEMININE,
                List.of(text, text + "и", text + "і", text + "у", text + "ою", text + "і", text + "о"));
        return new LocalizedName(phrase, phrase);
    }

    private static CellView cell(int n, int cells) {
        int x = n * SIDE;
        List<GridPoint> polygon = List.of(
                new GridPoint(x, 0), new GridPoint(x + SIDE, 0), new GridPoint(x + SIDE, SIDE), new GridPoint(x, SIDE));
        List<Integer> neighbors = new ArrayList<>();
        if (n > 0) {
            neighbors.add(n - 1);
        }
        if (n + 1 < cells) {
            neighbors.add(n + 1);
        }
        GridPoint site = new GridPoint(x + SIDE / 2, SIDE / 2);
        return switch (n % 4) {
            case 0, 1 -> {
                boolean river = n % 4 == 0 && n + 1 < cells;
                yield new CellView(
                        site,
                        polygon,
                        neighbors,
                        CellKind.LAND,
                        Optional.of(n % 8 == 0 ? Terrain.FOREST : Terrain.MOUNTAINS),
                        Optional.of(Relief.HILLS),
                        Optional.of(Climate.TEMPERATE),
                        OptionalInt.of(n % 100),
                        OptionalInt.of(40),
                        river,
                        river ? OptionalInt.of(n + 1) : OptionalInt.empty(),
                        n % 8 == 0 ? OptionalInt.empty() : OptionalInt.of(n % 2));
            }
            default ->
                new CellView(
                        site,
                        polygon,
                        neighbors,
                        n % 4 == 2 ? CellKind.SEA : CellKind.LAKE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        false,
                        OptionalInt.empty(),
                        OptionalInt.empty());
        };
    }
}
