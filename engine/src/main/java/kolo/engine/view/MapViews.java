package kolo.engine.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import kolo.engine.state.ControlType;
import kolo.engine.state.Country;
import kolo.engine.state.CountryId;
import kolo.engine.state.GameMap;
import kolo.engine.state.MapTile;
import kolo.engine.state.Province;
import kolo.engine.state.ProvinceId;
import kolo.engine.state.WorldState;

/**
 * Карта для гравця зі стану світу. Уся місцевість і кордони відкриті всім (GD §16.1 ховає армії, технології й
 * економіку, а не географію), тож фільтра за гравцем тут немає.
 */
public final class MapViews {

    private MapViews() {}

    public static MapView of(WorldState state) {
        Objects.requireNonNull(state, "state");
        GameMap map = state.map();
        List<CellView> cells = new ArrayList<>(map.tiles().size());
        for (int n = 0; n < map.tiles().size(); n++) {
            cells.add(cell(state, n));
        }
        TreeMap<CountryId, Integer> provinces = new TreeMap<>();
        for (Province province : state.provinces().values()) {
            province.owner().ifPresent(owner -> provinces.merge(owner, 1, Integer::sum));
        }
        List<CountryView> countries = new ArrayList<>(state.countries().size());
        for (Country country : state.countries().values()) {
            countries.add(new CountryView(
                    Math.toIntExact(country.id().number()),
                    country.name(),
                    country.control() != ControlType.NPC,
                    provinces.getOrDefault(country.id(), 0)));
        }
        // Номер держави — порядок генерації: так клієнт фарбує й підписує держави, як і раніше.
        countries.sort(Comparator.comparingInt(CountryView::number));
        return new MapView(state.seed(), map.width(), map.height(), cells, countries);
    }

    private static CellView cell(WorldState state, int n) {
        MapTile tile = state.map().tile(n);
        OptionalInt owner = OptionalInt.empty();
        if (tile.isLand()) {
            Optional<CountryId> id = state.provinces().get(ProvinceId.of(n)).owner();
            if (id.isPresent()) {
                owner = OptionalInt.of(Math.toIntExact(id.get().number()));
            }
        }
        return new CellView(
                tile.site(),
                tile.polygon(),
                tile.neighbors(),
                tile.kind(),
                tile.terrain(),
                tile.relief(),
                tile.climate(),
                tile.height(),
                tile.fertility(),
                tile.river(),
                tile.downstream(),
                owner);
    }
}
