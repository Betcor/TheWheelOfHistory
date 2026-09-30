package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Terrain;

/**
 * Мітки переважної місцевості держави (GD §4.1, № 3 — географія): якщо разом {@code terrains} займають щонайменше
 * {@code minPct} її провінцій, держава отримує {@code tags}. Частка округлюється вгору, як у {@link CoastLevelDef}.
 *
 * @param terrains типи місцевості, частки яких додаються; непорожні, без повторів
 * @param minPct з якої частки діє правило, {@code 1..100} %
 * @param tags мітки правила (напр. {@code mountainous}); непорожні — правило без міток нічого не дає
 */
public record TerrainTagDef(List<Terrain> terrains, int minPct, List<String> tags) {

    public TerrainTagDef {
        Objects.requireNonNull(terrains, "terrain_tag.terrains");
        if (terrains.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "terrain_tag.terrains"));
        }
        Defs.uniqueAll("terrain_tag.terrains", terrains);
        terrains = List.copyOf(terrains);
        Checks.inRange("terrain_tag.min_pct", minPct, 1, 100);
        tags = Defs.tags("terrain_tag.tags", tags);
        if (tags.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "terrain_tag.tags"));
        }
    }

    /** Чи правило діє за такої частки місцевостей правила, у відсотках вгору. */
    public boolean matches(int pct) {
        return pct >= minPct;
    }
}
