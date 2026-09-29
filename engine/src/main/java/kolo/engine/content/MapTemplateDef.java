package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Шаблон карти (GD §3.3): «Континенти», «Пангея», «Архіпелаг», «Земля-подібна» — і його сектор у колесі шаблону.
 *
 * @param name назва українською
 * @param description опис для лобі
 * @param weight вага в колесі шаблону, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує ваги всіх
 *     шаблонів
 * @param provincesPct коефіцієнт шаблону для кількості провінцій, {@code 1..}{@value #MAX_PROVINCES_PCT} %: 100 — без
 *     змін
 * @param landPct частка суходолу серед комірок карти, {@value #MIN_LAND_PCT}..{@value #MAX_LAND_PCT} %: провінцій суходолу
 *     рівно стільки, скільки дав розмір світу, решта комірок — море
 * @param continents скільки материків дає шаблон, {@code 1..}{@value #MAX_CONTINENTS}
 */
public record MapTemplateDef(
        MapTemplateId id,
        String name,
        String description,
        int weight,
        int provincesPct,
        int landPct,
        CountRange continents) {

    public static final int MAX_WEIGHT = 10_000;

    /** Уп'ятеро більше провінцій, ніж дає формула, — уже інший розмір світу, а не шаблон. */
    public static final int MAX_PROVINCES_PCT = 500;

    /** Більше материків на карті до 3500 провінцій — лише острівці, на яких не вміститься держава. */
    public static final int MAX_CONTINENTS = 12;

    /** Менше — комірок моря вдесятеро більше, ніж провінцій, і сітка найбільшого світу вже не вкладається в бюджет. */
    public static final int MIN_LAND_PCT = 10;

    /** Більше — моря лишається замало, щоб розділити материки. */
    public static final int MAX_LAND_PCT = 90;

    public MapTemplateDef {
        Objects.requireNonNull(id, "id");
        String field = "map_template." + id;
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange(field + ".provinces_pct", provincesPct, 1, MAX_PROVINCES_PCT);
        Checks.inRange(field + ".land_pct", landPct, MIN_LAND_PCT, MAX_LAND_PCT);
        Objects.requireNonNull(continents, field + ".continents");
        Checks.inRange(field + ".continents.min", continents.min(), 1, MAX_CONTINENTS);
        Checks.inRange(field + ".continents.max", continents.max(), continents.min(), MAX_CONTINENTS);
    }

    /** Скільки комірок сітки потрібно, щоб {@code provinces} провінцій суходолу склали {@code landPct} % карти (вгору). */
    public int gridCells(int provinces) {
        Checks.inRange("provinces", provinces, 1, Integer.MAX_VALUE);
        return Math.toIntExact(Math.ceilDiv((long) provinces * 100, landPct));
    }
}
