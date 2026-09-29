package kolo.engine.generation.map;

import java.util.List;
import java.util.Objects;
import kolo.engine.content.MapTemplateId;
import kolo.engine.error.Checks;
import kolo.engine.state.WorldLimits;
import kolo.engine.wheel.RollRecord;

/**
 * Розмір світу (GD §3.2–3.3) — результат {@link WorldSizeWheel}.
 *
 * @param players кількість гравців
 * @param npc кількість NPC-держав, {@code ≥ 0}; держав разом — не більше {@link WorldLimits#MAX_COUNTRIES}
 * @param provincesPerCountry результат колеса провінцій на державу, до коефіцієнта шаблону
 * @param provinces кількість провінцій на карті — з коефіцієнтом шаблону й обрізана межами балансу
 * @param unclaimedBp частка нічийних земель, bp
 * @param rolls обертання в порядку кидків; колеса, які зафіксував хост, не крутяться
 */
public record WorldSize(
        int players,
        int npc,
        MapTemplateId template,
        int continents,
        int provincesPerCountry,
        int provinces,
        int unclaimedBp,
        List<RollRecord> rolls) {

    public WorldSize {
        Checks.inRange("players", players, WorldLimits.MIN_PLAYERS, WorldLimits.MAX_PLAYERS);
        Checks.inRange("npc", npc, 0, WorldLimits.MAX_COUNTRIES - players);
        Objects.requireNonNull(template, "template");
        Checks.inRange("continents", continents, 1, Integer.MAX_VALUE);
        Checks.inRange("provinces_per_country", provincesPerCountry, 1, Integer.MAX_VALUE);
        Checks.inRange("provinces", provinces, 1, Integer.MAX_VALUE);
        Checks.inRange("unclaimed_bp", unclaimedBp, 0, 10_000);
        rolls = List.copyOf(rolls);
    }

    /** Держав разом — гравців і NPC. */
    public int countries() {
        return players + npc;
    }
}
