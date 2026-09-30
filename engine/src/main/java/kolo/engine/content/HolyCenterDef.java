package kolo.engine.content;

import kolo.engine.error.Checks;

/**
 * Числа колеса святого центру релігії (GD §25.1): вага провінції-кандидата.
 *
 * <p>Вага = ({@code base} + родючість × {@code fertilityPct} / 100 (вниз) + {@code river}, якщо провінцією тече
 * річка) × частка: провінція держави цієї віри — 100%, нічийна — {@code unclaimedPct}% (вниз), щонайменше 1. Святі
 * місця — землі обітовані: родючі долини й річки; нічийна земля — місце паломництва поза державами, тому рідше.
 *
 * @param base основа ваги кожної провінції, {@code 1..}{@value #MAX_WEIGHT}: без неї безплідна провінція не мала б
 *     шансу
 * @param fertilityPct скільки відсотків родючості провінції додається до ваги, {@code 0..}{@value #MAX_WEIGHT}
 * @param river добавка провінції з річкою, {@code 0..}{@value #MAX_WEIGHT}
 * @param unclaimedPct вага нічийної провінції у відсотках ваги провінції держави, {@code 1..100}; не 0 — релігія
 *     без держав-вірян стоїть саме на нічийній землі
 */
public record HolyCenterDef(int base, int fertilityPct, int river, int unclaimedPct) {

    /** Межа кожного числа ваги: більше не потрібно, щоб одна провінція переважила решту. */
    public static final int MAX_WEIGHT = 1000;

    public HolyCenterDef {
        Checks.inRange("religion.holy_center.base", base, 1, MAX_WEIGHT);
        Checks.inRange("religion.holy_center.fertility_pct", fertilityPct, 0, MAX_WEIGHT);
        Checks.inRange("religion.holy_center.river", river, 0, MAX_WEIGHT);
        Checks.inRange("religion.holy_center.unclaimed_pct", unclaimedPct, 1, 100);
    }

    /**
     * Вага провінції-кандидата, {@code ≥ 1}.
     *
     * @param unclaimed чи провінція нічийна
     * @param fertility родючість провінції, {@code 0..}{@value FertilityDef#MAX_VALUE}
     * @param hasRiver чи провінцією тече річка
     */
    public int weight(boolean unclaimed, int fertility, boolean hasRiver) {
        Checks.inRange("fertility", fertility, 0, FertilityDef.MAX_VALUE);
        int weight = base + fertility * fertilityPct / 100 + (hasRiver ? river : 0);
        return Math.max(1, unclaimed ? weight * unclaimedPct / 100 : weight);
    }
}
