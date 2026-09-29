package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Relief;

/**
 * Числа генерації рельєфу (GD §3.5): гірські хребти материків, шум і пороги висоти для рівнів рельєфу.
 *
 * <p>Висота провінції = {@code base_height} + внесок найближчого хребта ({@code ridge_height} мінус
 * {@code ridge_falloff} за кожен крок від нього, не менше 0) + шум у межах {@code ±noise_amplitude}, обрізано до
 * {@code 0..}{@value #MAX_HEIGHT}.
 *
 * @param ridges діапазон колеса кількості хребтів материка, {@code 0..}{@value #MAX_RIDGES}
 * @param ridgeMinProvinces скільки провінцій материка припадає щонайменше на один хребет,
 *     {@code 1..}{@value #MAX_RIDGE_MIN_PROVINCES}: малому материку колесо дає менше хребтів
 * @param ridgeLengthPct довжина хребта у відсотках «поперечника» материка (кореня з кількості провінцій),
 *     {@code 1..}{@value #MAX_RIDGE_LENGTH_PCT}
 * @param ridgeWander на скільки градусів хребет щонайбільше звертає за крок, {@code 0..}{@value #MAX_RIDGE_WANDER}
 * @param ridgeHeight надбавка висоти на самому хребті, {@code 0..}{@value #MAX_HEIGHT}
 * @param ridgeFalloff на скільки надбавка меншає за кожен крок від хребта, {@code 1..}{@value #MAX_HEIGHT}
 * @param baseHeight висота рівнини далеко від хребтів, {@code 0..}{@value #MAX_HEIGHT}
 * @param noiseAmplitude найбільший внесок шуму в будь-який бік, {@code 0..}{@value #MAX_HEIGHT}
 * @param noiseCells розмір плям шуму в комірках, {@code 1..}{@value ContinentsDef#MAX_NOISE_CELLS}
 * @param levels рівні рельєфу в порядку {@link Relief}, кожен рівно раз; пороги строго зростають, рівнина — з 0
 */
public record ReliefDef(
        CountRange ridges,
        int ridgeMinProvinces,
        int ridgeLengthPct,
        int ridgeWander,
        int ridgeHeight,
        int ridgeFalloff,
        int baseHeight,
        int noiseAmplitude,
        int noiseCells,
        List<ReliefLevelDef> levels) {

    /** Висота — {@code 0..MAX_HEIGHT}, як показники держави. */
    public static final int MAX_HEIGHT = 100;

    /** Більше хребтів на материку вже зливаються в суцільне нагір'я. */
    public static final int MAX_RIDGES = 10;

    public static final int MAX_RIDGE_MIN_PROVINCES = 1_000;

    /** Хребет удвічі довший за поперечник материка вже звивається вздовж берега. */
    public static final int MAX_RIDGE_LENGTH_PCT = 200;

    /** Хребет крокує лише до сусідів, що відхиляються від напрямку не більше ніж на 60°: більший поворот — злам. */
    public static final int MAX_RIDGE_WANDER = 60;

    public ReliefDef {
        Objects.requireNonNull(ridges, "relief.ridges");
        Checks.inRange("relief.ridges.max", ridges.max(), ridges.min(), MAX_RIDGES);
        Checks.inRange("relief.ridge_min_provinces", ridgeMinProvinces, 1, MAX_RIDGE_MIN_PROVINCES);
        Checks.inRange("relief.ridge_length_pct", ridgeLengthPct, 1, MAX_RIDGE_LENGTH_PCT);
        Checks.inRange("relief.ridge_wander", ridgeWander, 0, MAX_RIDGE_WANDER);
        Checks.inRange("relief.ridge_height", ridgeHeight, 0, MAX_HEIGHT);
        Checks.inRange("relief.ridge_falloff", ridgeFalloff, 1, MAX_HEIGHT);
        Checks.inRange("relief.base_height", baseHeight, 0, MAX_HEIGHT);
        Checks.inRange("relief.noise_amplitude", noiseAmplitude, 0, MAX_HEIGHT);
        Checks.inRange("relief.noise_cells", noiseCells, 1, ContinentsDef.MAX_NOISE_CELLS);
        levels = checkLevels(levels);
    }

    public ReliefLevelDef level(Relief relief) {
        return levels.get(relief.ordinal());
    }

    /** Найвищий рельєф, чий поріг не більший за {@code height}. */
    public Relief relief(int height) {
        Checks.inRange("height", height, 0, MAX_HEIGHT);
        Relief result = Relief.PLAIN;
        for (ReliefLevelDef level : levels) {
            if (level.minHeight() <= height) {
                result = level.relief();
            }
        }
        return result;
    }

    private static List<ReliefLevelDef> checkLevels(List<ReliefLevelDef> levels) {
        List<ReliefLevelDef> copy = List.copyOf(Objects.requireNonNull(levels, "relief.levels"));
        TreeMap<Relief, ReliefLevelDef> byRelief = new TreeMap<>();
        for (ReliefLevelDef level : copy) {
            if (byRelief.putIfAbsent(level.relief(), level) != null) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID,
                        ErrorDetails.of(
                                "field", "relief.id", "value", level.relief().key()));
            }
        }
        Defs.complete("relief.levels", byRelief, List.of(Relief.values()), Relief::key);
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).relief().ordinal() != i) {
                throw new ValidationException(
                        ErrorCode.OUT_OF_ORDER,
                        ErrorDetails.of(
                                "field",
                                "relief.id",
                                "value",
                                copy.get(i).relief().key()));
            }
        }
        // Рівнина — від нуля: інакше найнижчі провінції лишилися б без рельєфу.
        Checks.inRange("relief.plain.min_height", copy.getFirst().minHeight(), 0, 0);
        for (int i = 1; i < copy.size(); i++) {
            ReliefLevelDef.checkFollows(copy.get(i - 1), copy.get(i));
        }
        return copy;
    }
}
