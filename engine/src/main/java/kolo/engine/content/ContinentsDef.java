package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Числа генерації материків (GD §3.5): як ділиться суходіл між материками і яка в них берегова лінія.
 *
 * @param sizeWeight діапазон колеса ваги материка: рівні сектори від {@code min} до {@code max}
 *     ({@code 1..}{@value #MAX_SIZE_WEIGHT}); провінції суходолу діляться пропорційно вагам
 * @param minProvinces скільки провінцій щонайменше має кожен материк, {@code 1..}{@value #MAX_MIN_PROVINCES}
 * @param roughness порізаність берегів, {@code 0..100} %: шум множить відстань від зерна на {@code 1 ± roughness}; 0 —
 *     материк росте колом
 * @param noiseCells розмір плям шуму в комірках, {@code 1..}{@value #MAX_NOISE_CELLS}: більше — ширші затоки й
 *     півострови
 */
public record ContinentsDef(CountRange sizeWeight, int minProvinces, int roughness, int noiseCells) {

    /** Материк у сто разів більший за сусіда — уже острів біля материка, а не два материки. */
    public static final int MAX_SIZE_WEIGHT = 100;

    /** Нижня межа провінцій світу (400) мусить вміщувати мінімум кожного з 12 материків. */
    public static final int MAX_MIN_PROVINCES = 1_000;

    public static final int MAX_ROUGHNESS = 100;

    /** Плями, ширші за сотню комірок, на карті до 20 000 комірок уже не дають берегової лінії. */
    public static final int MAX_NOISE_CELLS = 100;

    public ContinentsDef {
        Objects.requireNonNull(sizeWeight, "continents.size_weight");
        Checks.inRange("continents.size_weight.min", sizeWeight.min(), 1, MAX_SIZE_WEIGHT);
        Checks.inRange("continents.size_weight.max", sizeWeight.max(), sizeWeight.min(), MAX_SIZE_WEIGHT);
        Checks.inRange("continents.min_provinces", minProvinces, 1, MAX_MIN_PROVINCES);
        Checks.inRange("continents.roughness", roughness, 0, MAX_ROUGHNESS);
        Checks.inRange("continents.noise_cells", noiseCells, 1, MAX_NOISE_CELLS);
    }
}
