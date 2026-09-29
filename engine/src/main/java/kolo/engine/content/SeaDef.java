package kolo.engine.content;

import kolo.engine.error.Checks;

/**
 * Числа генерації моря (GD §3.5): що з водойм — море, а що — озеро, і наскільки великі морські зони.
 *
 * @param minCells скільки комірок щонайменше має водойма, щоб бути морем, {@code 1..}{@value #MAX_MIN_CELLS}; менша —
 *     озеро: без морських зон і без виходу до моря
 * @param zoneCells скільки комірок моря в середньому на морську зону, {@code 1..}{@value #MAX_ZONE_CELLS}: море ділиться
 *     на {@code max(1, комірок / zoneCells)} зон з округленням до найближчого
 */
public record SeaDef(int minCells, int zoneCells) {

    /** Водойма понад тисячу комірок — уже океан на будь-якій карті. */
    public static final int MAX_MIN_CELLS = 1_000;

    /** Зона понад тисячу комірок на карті до 20 000 комірок — уже весь океан однією зоною. */
    public static final int MAX_ZONE_CELLS = 1_000;

    public SeaDef {
        Checks.inRange("sea.min_cells", minCells, 1, MAX_MIN_CELLS);
        Checks.inRange("sea.zone_cells", zoneCells, 1, MAX_ZONE_CELLS);
    }

    /** Скільки морських зон у морі з {@code cells} комірок: щонайменше одна, округлення до найближчого. */
    public int zones(int cells) {
        Checks.inRange("cells", cells, 1, Integer.MAX_VALUE);
        return Math.max(1, (int) ((cells + (long) zoneCells / 2) / zoneCells));
    }
}
