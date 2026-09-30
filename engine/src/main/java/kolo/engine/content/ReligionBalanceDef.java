package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Числа генерації релігій світу (GD §25.1): кількість, частини й святі центри.
 *
 * @param count таблиця кількості релігій за кількістю держав; рядки — за строго зростаючим {@code max_countries}
 * @param aspects скільки аспектів у божества, {@code 1..}{@value #MAX_PARTS}
 * @param dogmas скільки догматів у релігії, {@code 1..}{@value #MAX_PARTS}
 * @param holyCenter вага провінцій на колесі святого центру
 */
public record ReligionBalanceDef(
        List<ReligionCountDef> count, CountRange aspects, CountRange dogmas, HolyCenterDef holyCenter) {

    /** Більше аспектів чи догматів перевантажили б картку релігії й розмили б її характер. */
    public static final int MAX_PARTS = 6;

    /**
     * @throws ValidationException якщо таблиця порожня ({@link ErrorCode#EMPTY_COLLECTION}), її рядки не зростають
     *     за кількістю держав ({@link ErrorCode#OUT_OF_ORDER}) або кількість поза межами
     */
    public ReligionBalanceDef {
        count = List.copyOf(count);
        if (count.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "religion.count"));
        }
        for (int i = 1; i < count.size(); i++) {
            int maxCountries = count.get(i).maxCountries();
            if (maxCountries <= count.get(i - 1).maxCountries()) {
                throw new ValidationException(
                        ErrorCode.OUT_OF_ORDER,
                        ErrorDetails.of("field", "religion.count[" + i + "].max_countries", "value", maxCountries));
            }
        }
        check("religion.aspects", aspects);
        check("religion.dogmas", dogmas);
        Objects.requireNonNull(holyCenter, "holyCenter");
    }

    /**
     * Скільки релігій у світі з цією кількістю держав: перший рядок, що її вміщує; більше, ніж в останньому рядку, —
     * як в останньому.
     *
     * @param countries кількість держав у світі, {@code ≥ 1}
     */
    public CountRange religions(int countries) {
        Checks.inRange("countries", countries, 1, Integer.MAX_VALUE);
        for (ReligionCountDef row : count) {
            if (countries <= row.maxCountries()) {
                return row.religions();
            }
        }
        return count.getLast().religions();
    }

    private static void check(String field, CountRange range) {
        Objects.requireNonNull(range, field);
        Checks.inRange(field + ".min", range.min(), 1, MAX_PARTS);
        Checks.inRange(field + ".max", range.max(), range.min(), MAX_PARTS);
    }
}
