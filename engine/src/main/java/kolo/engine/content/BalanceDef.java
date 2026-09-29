package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.PowerCorridor;

/**
 * Числа балансу, що не належать жодному окремому визначенню: колеса, стріки, коридор сили, кількості генерації
 * країн і релігій.
 *
 * <p>Правила гри (межі переваги, мінімум КП/КУ, ліміт жетонів долі) — не тут, а в коді: вони не налаштовуються.
 *
 * @param corridors рівно по одному визначенню на кожен {@link PowerCorridor}
 */
public record BalanceDef(
        WheelBalanceDef wheel,
        StreakRulesDef streaks,
        SortedMap<PowerCorridor, PowerCorridorDef> corridors,
        GenerationBalanceDef generation,
        ReligionBalanceDef religion) {

    public BalanceDef {
        Objects.requireNonNull(wheel, "wheel");
        Objects.requireNonNull(streaks, "streaks");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(religion, "religion");
        TreeMap<PowerCorridor, PowerCorridorDef> copy = new TreeMap<>();
        corridors.forEach((key, def) -> {
            if (key != Objects.requireNonNull(def, "power_corridor").corridor()) {
                throw new ValidationException(
                        ErrorCode.UNKNOWN_REFERENCE,
                        ErrorDetails.of(
                                "field",
                                "power_corridor." + key.key(),
                                "value",
                                def.corridor().key()));
            }
            copy.put(key, def);
        });
        corridors = Defs.complete("power_corridors", copy, List.of(PowerCorridor.values()), PowerCorridor::key);
    }

    /** Зі списку визначень коридорів; повтор — {@link ErrorCode#DUPLICATE_ID}. */
    public static BalanceDef of(
            WheelBalanceDef wheel,
            StreakRulesDef streaks,
            List<PowerCorridorDef> corridors,
            GenerationBalanceDef generation,
            ReligionBalanceDef religion) {
        TreeMap<PowerCorridor, PowerCorridorDef> map = new TreeMap<>();
        for (PowerCorridorDef def : corridors) {
            Objects.requireNonNull(def, "power_corridor");
            if (map.putIfAbsent(def.corridor(), def) != null) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID,
                        ErrorDetails.of(
                                "field",
                                "power_corridor.id",
                                "value",
                                def.corridor().key()));
            }
        }
        return new BalanceDef(wheel, streaks, map, generation, religion);
    }

    public PowerCorridorDef corridor(PowerCorridor corridor) {
        return corridors.get(Objects.requireNonNull(corridor, "corridor"));
    }
}
