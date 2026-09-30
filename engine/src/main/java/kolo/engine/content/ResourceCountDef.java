package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Рядок таблиці кількості обертань колеса ресурсів (GD §4.5): держава до {@code maxProvinces} провінцій включно
 * отримує від {@code deposits.min()} до {@code deposits.max()} родовищ.
 *
 * @param maxProvinces верхня межа кількості провінцій держави рядка, {@code ≥ 1}
 * @param deposits скільки обертань, {@code 1..}{@value #MAX_DEPOSITS}
 */
public record ResourceCountDef(int maxProvinces, CountRange deposits) {

    /** Ресурси не повторюються: більше обертань, ніж ресурсів у контенті, нічого б не дали. */
    public static final int MAX_DEPOSITS = 10;

    public ResourceCountDef {
        Checks.inRange("resources.count.max_provinces", maxProvinces, 1, Integer.MAX_VALUE);
        Objects.requireNonNull(deposits, "deposits");
        Checks.inRange("resources.count.min", deposits.min(), 1, MAX_DEPOSITS);
        Checks.inRange("resources.count.max", deposits.max(), deposits.min(), MAX_DEPOSITS);
    }
}
