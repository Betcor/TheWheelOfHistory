package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Клімат світу (GD §3.5) — сектор колеса клімату світу: холодніший чи тепліший світ зсуває температуру всієї карти.
 *
 * @param name назва українською
 * @param description опис для лобі й енциклопедії
 * @param weight вага в колесі, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує ваги всіх кліматів світу
 * @param temperatureShift зсув температури кожної провінції, {@code ±}{@value #MAX_SHIFT}
 */
public record WorldClimateDef(WorldClimateId id, String name, String description, int weight, int temperatureShift) {

    public static final int MAX_WEIGHT = 10_000;

    /** Більший зсув робить увесь світ тропіками або льодовиком — шкала температури лише {@code 0..100}. */
    public static final int MAX_SHIFT = 50;

    public WorldClimateDef {
        Objects.requireNonNull(id, "id");
        String field = "world_climate." + id;
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange(field + ".temperature_shift", temperatureShift, -MAX_SHIFT, MAX_SHIFT);
    }
}
