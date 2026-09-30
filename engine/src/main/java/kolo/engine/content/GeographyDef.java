package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Географія держави (GD §4.1, № 3) — підсумок її території без колеса (рішення автора): вихід до моря й переважна
 * місцевість дають мітки для наступних коліс.
 *
 * @param coast рівні виходу до моря від найменшої частки прибережних провінцій; перший — з {@code 0}, тож рівень має
 *     кожна держава
 * @param terrains правила міток за переважною місцевістю в порядку контенту; держава отримує мітки кожного правила,
 *     що діє
 */
public record GeographyDef(List<CoastLevelDef> coast, List<TerrainTagDef> terrains) {

    /**
     * @throws ValidationException якщо рівнів виходу до моря немає ({@link ErrorCode#EMPTY_COLLECTION}), id
     *     повторюється ({@link ErrorCode#DUPLICATE_ID}), рівні не за зростанням порогу ({@link ErrorCode#OUT_OF_ORDER})
     *     або перший рівень не з нуля ({@link ErrorCode#VALUE_OUT_OF_RANGE})
     */
    public GeographyDef {
        Objects.requireNonNull(coast, "geography.coast");
        if (coast.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "geography.coast"));
        }
        TreeSet<CoastLevelId> seen = new TreeSet<>();
        CoastLevelDef previous = null;
        for (CoastLevelDef level : coast) {
            Objects.requireNonNull(level, "geography.coast_level");
            Defs.unique("geography.coast.id", seen, level.id());
            if (previous != null) {
                CoastLevelDef.checkFollows(previous, level);
            }
            previous = level;
        }
        // Інакше держава з малою часткою берега лишилася б без рівня.
        Checks.inRange("geography.coast[0].min_pct", coast.getFirst().minPct(), 0, 0);
        coast = List.copyOf(coast);
        Objects.requireNonNull(terrains, "geography.terrains");
        terrains.forEach(rule -> Objects.requireNonNull(rule, "geography.terrain_tag"));
        terrains = List.copyOf(terrains);
    }

    /** Рівень виходу до моря за часткою прибережних провінцій у відсотках вгору, {@code 0..100}. */
    public CoastLevelDef coastLevel(int coastalPct) {
        Checks.inRange("coastal_pct", coastalPct, 0, 100);
        CoastLevelDef found = coast.getFirst();
        for (CoastLevelDef level : coast) {
            if (level.minPct() <= coastalPct) {
                found = level;
            }
        }
        return found;
    }

    /** Мітки, які може дати географія. */
    public TreeSet<String> producedTags() {
        TreeSet<String> tags = new TreeSet<>();
        coast.forEach(level -> tags.addAll(level.tags()));
        terrains.forEach(rule -> tags.addAll(rule.tags()));
        return tags;
    }
}
