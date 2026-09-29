package kolo.engine.content;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.error.Checks;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;

/**
 * Числа розміру світу (GD §3.2–3.3).
 *
 * @param npcExtra на кожну {@link NpcShare}: скільки NPC додається до кількості гравців, {@code 0..}{@link
 *     WorldLimits#MAX_COUNTRIES}
 * @param provincesPerCountry провінцій на державу до коефіцієнта шаблону, {@code ≥ 1}
 * @param unclaimedBp частка нічийних земель у базисних пунктах, {@code 0..}{@value #MAX_UNCLAIMED_BP}
 * @param provinces межі кількості провінцій на карті, {@code min ≥ 1}
 */
public record WorldBalanceDef(
        SortedMap<NpcShare, CountRange> npcExtra,
        StepRange provincesPerCountry,
        StepRange unclaimedBp,
        CountRange provinces) {

    /** Більше половини нічийної землі — світ колоністів, а не держав. */
    public static final int MAX_UNCLAIMED_BP = 5_000;

    public WorldBalanceDef {
        TreeMap<NpcShare, CountRange> copy = new TreeMap<>();
        for (Map.Entry<NpcShare, CountRange> entry : npcExtra.entrySet()) {
            String field = "world.npc_extra." + entry.getKey().key();
            CountRange range = Objects.requireNonNull(entry.getValue(), field);
            Checks.inRange(field + ".max", range.max(), range.min(), WorldLimits.MAX_COUNTRIES);
            copy.put(entry.getKey(), range);
        }
        npcExtra = Defs.complete("world.npc_extra", copy, List.of(NpcShare.values()), NpcShare::key);
        Objects.requireNonNull(provincesPerCountry, "world.provinces_per_country");
        Checks.inRange("world.provinces_per_country.min", provincesPerCountry.min(), 1, Integer.MAX_VALUE);
        Objects.requireNonNull(unclaimedBp, "world.unclaimed_bp");
        Checks.inRange("world.unclaimed_bp.max", unclaimedBp.max(), unclaimedBp.min(), MAX_UNCLAIMED_BP);
        Objects.requireNonNull(provinces, "world.provinces");
        Checks.inRange("world.provinces.min", provinces.min(), 1, Integer.MAX_VALUE);
    }

    public CountRange npcExtra(NpcShare share) {
        return npcExtra.get(Objects.requireNonNull(share, "share"));
    }
}
