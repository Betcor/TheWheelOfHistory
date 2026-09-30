package kolo.engine.generation.map;

import java.util.Objects;
import java.util.TreeMap;
import kolo.engine.content.ContentPack;
import kolo.engine.content.FertilityDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Родючість провінцій (GD §3.5) — таблиця {@link FertilityDef} за поясом, типом місцевості, вологою й річкою. Ні коліс,
 * ні кидків: родючість — наслідок клімату й річок.
 */
public final class FertilityGenerator {

    private FertilityGenerator() {}

    /**
     * @param climate пояс, місцевість і волога суходолу
     * @param rivers річки тієї самої карти
     */
    public static FertilityMap generate(ContentPack content, ClimateMap climate, RiverMap rivers) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(rivers, "rivers");
        if (!climate.climates().keySet().equals(rivers.downstream().keySet())) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "rivers"));
        }
        FertilityDef def = content.map().fertility();
        TreeMap<Integer, Integer> fertilities = new TreeMap<>();
        for (int cell : climate.climates().keySet()) {
            fertilities.put(
                    cell,
                    def.fertility(
                            climate.climates().get(cell),
                            climate.terrains().get(cell),
                            climate.moistures().get(cell),
                            rivers.hasRiver(cell)));
        }
        return new FertilityMap(fertilities);
    }
}
