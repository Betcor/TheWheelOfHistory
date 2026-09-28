package kolo.engine.state;

import java.util.function.ToLongFunction;
import kolo.engine.error.Checks;

/**
 * Показники держави. У стані зберігаються лише базові значення, без модифікаторів; ефективні рахує
 * {@link kolo.engine.modifier.Modifiers}.
 *
 * @param gdp ВВП, гроші/рік
 * @param stability стабільність, {@code 0..100}
 * @param hdi ІЛР, {@code 0..100}
 * @param influence вплив, {@code 0..100}
 * @param legitimacy легітимність, {@code 0..100}
 * @param warWeariness втома від війни, {@code 0..100}
 * @param science науковий потенціал, очки/рік
 */
public record CountryStats(
        long gdp, int stability, int hdi, int influence, int legitimacy, int warWeariness, int science) {

    public CountryStats {
        requireInRange(Stat.GDP, gdp);
        requireInRange(Stat.STABILITY, stability);
        requireInRange(Stat.HDI, hdi);
        requireInRange(Stat.INFLUENCE, influence);
        requireInRange(Stat.LEGITIMACY, legitimacy);
        requireInRange(Stat.WAR_WEARINESS, warWeariness);
        requireInRange(Stat.SCIENCE, science);
    }

    /** Набір статів, де кожен показник задає функція; значення мають бути в межах показників. */
    public static CountryStats from(ToLongFunction<Stat> values) {
        return new CountryStats(
                values.applyAsLong(Stat.GDP),
                Math.toIntExact(values.applyAsLong(Stat.STABILITY)),
                Math.toIntExact(values.applyAsLong(Stat.HDI)),
                Math.toIntExact(values.applyAsLong(Stat.INFLUENCE)),
                Math.toIntExact(values.applyAsLong(Stat.LEGITIMACY)),
                Math.toIntExact(values.applyAsLong(Stat.WAR_WEARINESS)),
                Math.toIntExact(values.applyAsLong(Stat.SCIENCE)));
    }

    private static void requireInRange(Stat stat, long value) {
        Checks.inRange("stats." + stat.name(), value, stat.min(), stat.max());
    }
}
