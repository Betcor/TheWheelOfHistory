package kolo.engine.generation.map;

import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DepositDef;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Придатність провінцій до родовищ ресурсів (GD §4.5) — таблиця {@link DepositDef} за поясом, типом місцевості й
 * родючістю. Ні коліс, ні кидків: карта лише каже, де що може лежати; родовища кладе колесо ресурсів держави, коли
 * відомі її провінції.
 */
public final class ResourceSuitabilityGenerator {

    private ResourceSuitabilityGenerator() {}

    /**
     * @param climate пояс і місцевість суходолу
     * @param fertility родючість тієї самої карти
     */
    public static ResourceSuitabilityMap generate(ContentPack content, ClimateMap climate, FertilityMap fertility) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(fertility, "fertility");
        if (!climate.climates().keySet().equals(fertility.fertilities().keySet())) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "fertility"));
        }
        TreeMap<Integer, SortedMap<ResourceId, Integer>> suitabilities = new TreeMap<>();
        for (int cell : climate.climates().keySet()) {
            TreeMap<ResourceId, Integer> resources = new TreeMap<>();
            for (ResourceDef resource : content.resources().values()) {
                if (resource.deposits().isEmpty()) {
                    continue;
                }
                int suitability = resource.deposits()
                        .get()
                        .suitability(
                                climate.climates().get(cell),
                                climate.terrains().get(cell),
                                fertility.fertilities().get(cell));
                if (suitability > 0) {
                    resources.put(resource.id(), suitability);
                }
            }
            suitabilities.put(cell, resources);
        }
        return new ResourceSuitabilityMap(suitabilities);
    }
}
