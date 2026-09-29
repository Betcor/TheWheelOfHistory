package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Колесо релігії держави (GD §4.1, 6а; §25.2): сектори — релігії світу й світська держава.
 *
 * @param religionWeight вага сектору кожної релігії світу, {@code 1..}{@value #MAX_WEIGHT}; відносна — однакова для
 *     всіх релігій
 * @param secular сектор світської держави
 */
public record StateReligionDef(int religionWeight, SecularStateDef secular) {

    public static final int MAX_WEIGHT = 10_000;

    public StateReligionDef {
        Checks.inRange("state_religion.religion_weight", religionWeight, 1, MAX_WEIGHT);
        Objects.requireNonNull(secular, "secular");
    }
}
