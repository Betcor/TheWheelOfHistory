package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.PowerCorridor;

/**
 * Межі коридору бюджету сили (GD §4.11): поки проміжна сила держави в них, колеса генерації не зсуваються.
 *
 * @param players коридор для держав гравців
 * @param npc коридор для NPC; містить коридор гравців, бо світ має мати і наддержави, і карликові держави
 */
public record PowerCorridorDef(PowerCorridor corridor, MedianRange players, MedianRange npc) {

    public PowerCorridorDef {
        Objects.requireNonNull(corridor, "corridor");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(npc, "npc");
        String field = "power_corridor." + corridor.key() + ".npc";
        Checks.inRange(field + ".min_pct", npc.minPct(), 1, players.minPct());
        Checks.inRange(field + ".max_pct", npc.maxPct(), players.maxPct(), MedianRange.MAX_PCT);
    }

    /** @param npc чи генерується держава NPC */
    public MedianRange range(boolean npc) {
        return npc ? this.npc : players;
    }
}
