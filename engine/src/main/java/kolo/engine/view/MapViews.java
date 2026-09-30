package kolo.engine.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import kolo.engine.generation.map.MapCell;
import kolo.engine.generation.map.PlacementMap;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.world.StartWorld;
import kolo.engine.state.LocalizedName;

/**
 * Карта для гравця зі згенерованого світу. Уся місцевість і кордони на старті відкриті всім (GD §16.1 ховає армії,
 * технології й економіку, а не географію), тож фільтра за гравцем тут немає.
 */
public final class MapViews {

    private MapViews() {}

    /** @param seed seed, з якого згенеровано світ */
    public static MapView of(long seed, StartWorld world) {
        Objects.requireNonNull(world, "world");
        WorldMap map = world.map();
        List<CellView> cells = new ArrayList<>(map.grid().cells().size());
        for (int n = 0; n < map.grid().cells().size(); n++) {
            cells.add(cell(map, n));
        }
        List<CountryView> countries = new ArrayList<>(world.countries().size());
        for (int n = 0; n < world.countries().size(); n++) {
            LocalizedName name = world.country(n).name().name();
            countries.add(new CountryView(
                    n, name, n < map.size().players(), map.country(n).provinces()));
        }
        return new MapView(seed, map.grid().width(), map.grid().height(), cells, countries);
    }

    private static CellView cell(WorldMap map, int n) {
        MapCell cell = map.grid().cells().get(n);
        CellKind kind = map.sea().isSea(n) ? CellKind.SEA : map.sea().isLake(n) ? CellKind.LAKE : CellKind.LAND;
        boolean river = map.rivers().hasRiver(n);
        int owner = map.placement().country(n);
        return new CellView(
                cell.site(),
                cell.polygon(),
                cell.neighbors(),
                kind,
                map.climate().terrain(n),
                map.relief().relief(n),
                map.climate().climate(n),
                map.relief().height(n),
                map.fertility().fertility(n),
                river,
                river ? map.rivers().downstream(n) : OptionalInt.empty(),
                owner == PlacementMap.NONE ? OptionalInt.empty() : OptionalInt.of(owner));
    }
}
