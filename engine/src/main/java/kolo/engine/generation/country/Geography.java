package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.CoastLevelDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.GeographyDef;
import kolo.engine.content.TerrainTagDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.ClimateMap;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.generation.map.SeaMap;
import kolo.engine.state.Terrain;

/**
 * Географія держави (GD §4.1, № 3): підсумок її території без колеса й без кидків (рішення автора). Територію вже
 * обрали колеса материка й площі та розміщення, тож колесо лише суперечило б карті.
 *
 * <ul>
 *   <li>Вихід до моря — рівень з {@code geography.coast} за часткою провінцій, що межують з морем (озеро не рахується),
 *       і перелік сусідніх морських зон.
 *   <li>Місцевість — кількість провінцій кожного типу, переважний тип і мітки кожного правила {@code
 *       geography.terrains}, чиї типи разом займають щонайменше поріг.
 *   <li>Середня родючість — для колеса населення.
 * </ul>
 */
public final class Geography {

    private Geography() {}

    /**
     * @param provinces комірки суходолу держави; порядок і повтори не важливі
     * @throws ValidationException якщо провінцій немає ({@link ErrorCode#EMPTY_COLLECTION}) або серед них є вода чи
     *     комірка іншої карти ({@link ErrorCode#VALUE_OUT_OF_RANGE})
     */
    public static StartGeography generate(
            ContentPack content,
            Collection<Integer> provinces,
            SeaMap sea,
            ClimateMap climate,
            FertilityMap fertility) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(sea, "sea");
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(fertility, "fertility");
        SortedSet<Integer> cells = new TreeSet<>(provinces);
        if (cells.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "provinces"));
        }
        GeographyDef def = content.map().geography();

        List<Integer> coastal = new ArrayList<>();
        TreeSet<Integer> zones = new TreeSet<>();
        EnumMap<Terrain, Integer> terrains = new EnumMap<>(Terrain.class);
        long fertilitySum = 0;
        for (int cell : cells) {
            if (cell < 0
                    || cell >= sea.cellBodies().size()
                    || climate.terrain(cell).isEmpty()
                    || fertility.fertility(cell).isEmpty()) {
                throw new ValidationException(
                        ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "provinces", "value", cell));
            }
            if (sea.coastal(cell)) {
                coastal.add(cell);
                zones.addAll(sea.seaZones(cell));
            }
            terrains.merge(climate.terrain(cell).orElseThrow(), 1, Integer::sum);
            fertilitySum += fertility.fertility(cell).getAsInt();
        }

        Terrain dominant = null;
        for (Map.Entry<Terrain, Integer> entry : terrains.entrySet()) {
            if (dominant == null || entry.getValue() > terrains.get(dominant)) {
                dominant = entry.getKey();
            }
        }
        CoastLevelDef coast = def.coastLevel(StartGeography.percentUp(coastal.size(), cells.size()));
        TreeSet<String> tags = new TreeSet<>(coast.tags());
        for (TerrainTagDef rule : def.terrains()) {
            int count = 0;
            for (Terrain kind : rule.terrains()) {
                count += terrains.getOrDefault(kind, 0);
            }
            if (rule.matches(StartGeography.percentUp(count, cells.size()))) {
                tags.addAll(rule.tags());
            }
        }
        return new StartGeography(
                List.copyOf(cells),
                coastal,
                coast.id(),
                List.copyOf(zones),
                new TreeMap<>(terrains),
                dominant,
                Math.toIntExact(fertilitySum / cells.size()),
                tags);
    }
}
