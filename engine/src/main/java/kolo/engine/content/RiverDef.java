package kolo.engine.content;

import kolo.engine.error.Checks;

/**
 * Числа генерації річок (GD §3.5): з якого стоку вода в провінції вже річка і яка річка не надто коротка.
 *
 * @param minFlow найменший стік річкової провінції, {@code 101..}{@value #MAX_MIN_FLOW}: стік — сума вологи провінції
 *     й усіх провінцій, що стікають через неї; більше за {@value ClimateDef#MAX_VALUE}, бо одна провінція сама по собі
 *     річки не дає — потрібен басейн
 * @param minCells найменше провінцій у річковій системі (річка з притоками до одного гирла), {@code 1..}{@value
 *     #MAX_MIN_CELLS}: коротша система — струмок біля берега, не річка
 */
public record RiverDef(int minFlow, int minCells) {

    /** Стік понад мільйон — більше за всю вологу найбільшої карти (20 000 комірок × 100): річок не було б. */
    public static final int MAX_MIN_FLOW = 1_000_000;

    /** Система понад сотню провінцій — уже найбільші ріки материка: більший поріг лишив би карту без річок. */
    public static final int MAX_MIN_CELLS = 100;

    public RiverDef {
        Checks.inRange("rivers.min_flow", minFlow, ClimateDef.MAX_VALUE + 1, MAX_MIN_FLOW);
        Checks.inRange("rivers.min_cells", minCells, 1, MAX_MIN_CELLS);
    }

    /** Чи стік {@code flow} достатній для річки; річкою комірка стане, якщо її система не коротша за {@code minCells}. */
    public boolean enoughFlow(int flow) {
        return flow >= minFlow;
    }
}
