package kolo.engine.generation.religion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kolo.engine.wheel.RollRecord;

/**
 * Релігії світу на старті (GD §25.1) — результат {@link WorldReligionsWheel}.
 *
 * @param religions релігії в порядку генерації; повні назви різні
 * @param countRoll обертання колеса кількості релігій
 */
public record StartReligions(List<StartReligion> religions, RollRecord countRoll) {

    public StartReligions {
        religions = List.copyOf(religions);
        Objects.requireNonNull(countRoll, "countRoll");
    }

    /** Усі обертання в порядку кидків: кількість релігій, далі обертання кожної релігії. */
    public List<RollRecord> rolls() {
        List<RollRecord> rolls = new ArrayList<>();
        rolls.add(countRoll);
        religions.forEach(religion -> rolls.addAll(religion.rolls()));
        return List.copyOf(rolls);
    }
}
