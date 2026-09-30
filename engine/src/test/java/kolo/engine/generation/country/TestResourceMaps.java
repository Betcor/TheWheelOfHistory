package kolo.engine.generation.country;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.content.ContentPack;
import kolo.engine.content.ResourceId;
import kolo.engine.content.TestResources;
import kolo.engine.generation.map.ResourceSuitabilityMap;
import kolo.engine.generation.name.TestNames;

/** Карти придатності до родовищ, складені вручну, для тестів колеса ресурсів. */
final class TestResourceMaps {

    static final ContentPack PACK = TestNames.pack(TestResources.RESOURCES, TestResources.BALANCE);

    /**
     * Чотири комірки: 0 — руда 40; 1 — руда 20 і ліс 50; 2 — зерно 60; 3 — нічого. Суми: руда 60, ліс 50, зерно 60;
     * комірка 10 — вода.
     */
    static final ResourceSuitabilityMap SMALL = map(Map.of(
            0, Map.of(TestResources.ORE, 40),
            1, Map.of(TestResources.ORE, 20, TestResources.WOOD, 50),
            2, Map.of(TestResources.GRAIN, 60),
            3, Map.of()));

    private TestResourceMaps() {}

    static ResourceSuitabilityMap map(Map<Integer, Map<ResourceId, Integer>> values) {
        TreeMap<Integer, SortedMap<ResourceId, Integer>> copy = new TreeMap<>();
        values.forEach((cell, resources) -> copy.put(cell, new TreeMap<>(resources)));
        return new ResourceSuitabilityMap(copy);
    }

    /** {@code cells} комірок, у кожній — усі чотири ресурси карти з придатністю 10. */
    static ResourceSuitabilityMap uniform(int cells) {
        TreeMap<Integer, Map<ResourceId, Integer>> values = new TreeMap<>();
        for (int cell = 0; cell < cells; cell++) {
            values.put(
                    cell,
                    Map.of(
                            TestResources.ORE, 10,
                            TestResources.WOOD, 10,
                            TestResources.GRAIN, 10,
                            TestResources.SALT, 10));
        }
        return map(values);
    }
}
