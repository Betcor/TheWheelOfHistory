package kolo.engine.generation.religion;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.wheel.RollRecord;

/**
 * Святий центр релігії (GD §25.1) — провінція, яку релігія вважає священною.
 *
 * @param cell комірка суходолу; у держави цієї віри або нічийна (якщо таких немає — будь-яка)
 * @param roll обертання колеса святого центру
 */
public record HolyCenter(int cell, RollRecord roll) {

    public HolyCenter {
        Checks.inRange("holy_center.cell", cell, 0, Integer.MAX_VALUE);
        Objects.requireNonNull(roll, "roll");
    }
}
