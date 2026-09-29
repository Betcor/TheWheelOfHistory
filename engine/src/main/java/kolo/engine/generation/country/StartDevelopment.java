package kolo.engine.generation.country;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Development;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.RollRecord;

/**
 * Стартова технологічна розвиненість держави (GD §4.3) — результат {@link DevelopmentWheel}.
 *
 * @param levels рівень кожної галузі, {@link Development#MIN}..{@link Development#MAX}; визначено всі галузі
 * @param tags мітки обраних рівнів без повторів — вхід для наступних коліс генерації
 * @param quality якість розвиненості для стріків генерації (GD §4.10), {@code 0..100}: чотири галузі — один
 *     результат, інакше одна вдала розвиненість майже гарантувала б стрік
 * @param rolls обертання в порядку галузей {@link TechBranch}
 */
public record StartDevelopment(
        Map<TechBranch, Integer> levels, SortedSet<String> tags, int quality, List<RollRecord> rolls) {

    public StartDevelopment {
        EnumMap<TechBranch, Integer> copy = new EnumMap<>(TechBranch.class);
        copy.putAll(levels);
        for (TechBranch branch : TechBranch.values()) {
            Integer level = copy.get(branch);
            if (level == null) {
                throw new ValidationException(
                        ErrorCode.MISSING_DEFINITION, ErrorDetails.of("field", "levels", "value", branch.key()));
            }
            Development.check("levels." + branch.key(), level);
        }
        levels = Collections.unmodifiableMap(copy);
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        Checks.inRange("quality", quality, 0, 100);
        rolls = List.copyOf(rolls);
    }

    /** Рівень галузі. */
    public int level(TechBranch branch) {
        return levels.get(branch);
    }

    /**
     * Внесок галузі в перевагу колеса генерації: рівень × {@code perLevel}. Рядок пояснення має id {@code
     * development:<галузь>} і ключ {@code development.<галузь>}; на світовому рівні внеску немає.
     */
    public Optional<AppliedModifier> advantage(TechBranch branch, int perLevel) {
        int value = level(branch) * perLevel;
        if (value == 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedModifier("development:" + branch.key(), "development." + branch.key(), value));
    }
}
