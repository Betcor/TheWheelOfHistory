package kolo.engine.generation.map;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.MapTemplateId;
import kolo.engine.error.Checks;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;

/**
 * Параметри сесії для розміру світу (GD §3.4), які задає хост.
 *
 * @param players кількість гравців, {@link WorldLimits#MIN_PLAYERS}..{@link WorldLimits#MAX_PLAYERS}
 * @param template шаблон карти, зафіксований хостом; порожньо — крутить колесо
 * @param continents кількість материків, зафіксована хостом, {@code 1..}{@value MapTemplateDef#MAX_CONTINENTS};
 *     порожньо — крутить колесо в межах шаблону
 */
public record WorldSizeInput(int players, NpcShare npcShare, Optional<MapTemplateId> template, OptionalInt continents) {

    public WorldSizeInput {
        Checks.inRange("players", players, WorldLimits.MIN_PLAYERS, WorldLimits.MAX_PLAYERS);
        Objects.requireNonNull(npcShare, "npc_share");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(continents, "continents");
        continents.ifPresent(count -> Checks.inRange("continents", count, 1, MapTemplateDef.MAX_CONTINENTS));
    }

    /** Без зафіксованих шаблону й материків. */
    public static WorldSizeInput of(int players, NpcShare npcShare) {
        return new WorldSizeInput(players, npcShare, Optional.empty(), OptionalInt.empty());
    }
}
