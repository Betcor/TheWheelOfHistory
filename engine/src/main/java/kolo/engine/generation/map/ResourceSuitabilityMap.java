package kolo.engine.generation.map;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.content.DepositDef;
import kolo.engine.content.ResourceId;
import kolo.engine.error.Checks;

/**
 * Придатність суходолу до родовищ — результат {@link ResourceSuitabilityGenerator}. Самих родовищ тут немає: їх
 * кладе колесо ресурсів держави.
 *
 * @param suitabilities комірка суходолу → ресурс → придатність {@code 1..}{@value DepositDef#MAX_SUITABILITY};
 *     нульова придатність не зберігається, тож комірка може мати порожню мапу
 */
public record ResourceSuitabilityMap(SortedMap<Integer, SortedMap<ResourceId, Integer>> suitabilities) {

    public ResourceSuitabilityMap {
        TreeMap<Integer, SortedMap<ResourceId, Integer>> copy = new TreeMap<>();
        for (Map.Entry<Integer, SortedMap<ResourceId, Integer>> cell : suitabilities.entrySet()) {
            Checks.inRange("cell", cell.getKey(), 0, Integer.MAX_VALUE);
            TreeMap<ResourceId, Integer> resources = new TreeMap<>();
            for (Map.Entry<ResourceId, Integer> entry : cell.getValue().entrySet()) {
                Objects.requireNonNull(entry.getKey(), "resource");
                resources.put(
                        entry.getKey(), Checks.inRange("suitability", entry.getValue(), 1, DepositDef.MAX_SUITABILITY));
            }
            copy.put(cell.getKey(), Collections.unmodifiableSortedMap(resources));
        }
        suitabilities = Collections.unmodifiableSortedMap(copy);
    }

    /** Чи комірка — суходіл цієї карти. */
    public boolean isLand(int cell) {
        return suitabilities.containsKey(cell);
    }

    /** Придатність комірки до родовища ресурсу; 0 — родовища тут не буває або комірка — вода. */
    public int suitability(int cell, ResourceId resource) {
        SortedMap<ResourceId, Integer> resources = suitabilities.get(cell);
        return resources == null ? 0 : resources.getOrDefault(Objects.requireNonNull(resource, "resource"), 0);
    }
}
