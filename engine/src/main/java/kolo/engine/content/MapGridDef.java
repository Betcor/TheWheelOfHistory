package kolo.engine.content;

import kolo.engine.error.Checks;

/**
 * Числа сітки комірок Вороного (GD §3.5): з них карта отримує розміри, а провінції — форму.
 *
 * @param cellSize середня сторона комірки в одиницях карти, {@value #MIN_CELL_SIZE}..{@value #MAX_CELL_SIZE}: площа карти = комірок × {@code cellSize²}
 * @param aspectWidth ширина в пропорції карти, {@code 1..}{@value #MAX_ASPECT}
 * @param aspectHeight висота в пропорції карти, {@code 1..}{@value #MAX_ASPECT}
 * @param relaxation скільки ітерацій релаксації Ллойда, {@code 0..}{@value #MAX_RELAXATION}: 0 — випадкові точки
 *     без вирівнювання
 */
public record MapGridDef(int cellSize, int aspectWidth, int aspectHeight, int relaxation) {

    /**
     * Вершини комірок округлюються до цілих, а близькі зливаються: на меншій комірці похибка в одиницю вже помітна в
     * її формі.
     */
    public static final int MIN_CELL_SIZE = 20;

    /** Більша комірка на {@value #MAX_CELLS} комірок уже наближається до меж {@code int} у площі карти. */
    public static final int MAX_CELL_SIZE = 1_000;

    /** Карта, вужча за 1:4 в будь-який бік, — уже смуга, а не світ. */
    public static final int MAX_ASPECT = 4;

    /** Після кількох ітерацій комірки майже не змінюються, а кожна ітерація — ще одна діаграма Вороного. */
    public static final int MAX_RELAXATION = 10;

    /** Найбільша сітка: 3500 провінцій суходолу з морем при найменшій частці суходолу шаблону. */
    public static final int MAX_CELLS = 20_000;

    public MapGridDef {
        Checks.inRange("map_grid.cell_size", cellSize, MIN_CELL_SIZE, MAX_CELL_SIZE);
        Checks.inRange("map_grid.aspect.width", aspectWidth, 1, MAX_ASPECT);
        Checks.inRange("map_grid.aspect.height", aspectHeight, 1, MAX_ASPECT);
        Checks.inRange("map_grid.relaxation", relaxation, 0, MAX_RELAXATION);
    }
}
