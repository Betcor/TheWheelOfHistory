package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Колесо населення держави (GD §4.1, колесо 4) і розподіл населення по її провінціях.
 *
 * @param levels рівні від найменшого до найбільшого — порядок секторів колеса
 * @param areaAdvantage перевага за кожні 100 відсоткових пунктів площі понад середню ({@link AreaLevelDef#sharePct()}
 *     − 100), {@code 0..}{@value #MAX_ADVANTAGE}; менша держава отримує від'ємну
 * @param fertilityAdvantage скільки відсотків різниці середньої родючості держави й світу йде в перевагу, {@code
 *     0..}{@value #MAX_ADVANTAGE}
 * @param provinceBase вага кожної провінції в розподілі населення, {@code 1..}{@value #MAX_PROVINCE_WEIGHT}: до неї
 *     додаються родючість і бонус узбережжя; більше — рівніше населення між родючими й пустими провінціями
 * @param coastBonus добавка до ваги провінції з виходом до моря, {@code 0..}{@value #MAX_PROVINCE_WEIGHT}
 */
public record PopulationDef(
        List<PopulationLevelDef> levels, int areaAdvantage, int fertilityAdvantage, int provinceBase, int coastBonus) {

    public static final int MAX_ADVANTAGE = 100;

    /** Родючість — {@code 0..100}; вага в десять разів більша вже робить розподіл майже рівним. */
    public static final int MAX_PROVINCE_WEIGHT = 1_000;

    /**
     * @throws ValidationException якщо рівнів немає ({@link ErrorCode#EMPTY_COLLECTION}), id повторюється
     *     ({@link ErrorCode#DUPLICATE_ID}), рівні не по порядку ({@link ErrorCode#OUT_OF_ORDER}) або число поза
     *     межами ({@link ErrorCode#VALUE_OUT_OF_RANGE})
     */
    public PopulationDef {
        Objects.requireNonNull(levels, "population.levels");
        if (levels.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "population.levels"));
        }
        TreeSet<PopulationLevelId> seen = new TreeSet<>();
        PopulationLevelDef previous = null;
        for (PopulationLevelDef level : levels) {
            Objects.requireNonNull(level, "population.level");
            Defs.unique("population.levels.id", seen, level.id());
            if (previous != null) {
                PopulationLevelDef.checkFollows(previous, level);
            }
            previous = level;
        }
        levels = List.copyOf(levels);
        Checks.inRange("population.area_advantage", areaAdvantage, 0, MAX_ADVANTAGE);
        Checks.inRange("population.fertility_advantage", fertilityAdvantage, 0, MAX_ADVANTAGE);
        // Держава з пустих провінцій без берега мала б нульову суму ваг — населення нікуди поділити.
        Checks.inRange("population.province_base", provinceBase, 1, MAX_PROVINCE_WEIGHT);
        Checks.inRange("population.coast_bonus", coastBonus, 0, MAX_PROVINCE_WEIGHT);
    }

    public Optional<PopulationLevelDef> level(PopulationLevelId id) {
        Objects.requireNonNull(id, "id");
        return levels.stream().filter(level -> level.id().equals(id)).findFirst();
    }

    /** Мітки, які можуть дати рівні населення. */
    public TreeSet<String> producedTags() {
        TreeSet<String> tags = new TreeSet<>();
        levels.forEach(level -> tags.addAll(level.tags()));
        return tags;
    }
}
