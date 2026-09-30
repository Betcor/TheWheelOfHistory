package kolo.engine.generation.religion;

import java.util.List;
import kolo.engine.wheel.RollRecord;

/**
 * Святі центри релігій світу — результат {@link HolyCenterWheel}.
 *
 * @param centers святий центр кожної релігії за її номером у {@link StartReligions#religions()}; одна провінція може
 *     бути святою для кількох релігій
 */
public record StartHolyCenters(List<HolyCenter> centers) {

    public StartHolyCenters {
        centers = List.copyOf(centers);
    }

    /** Комірка святого центру релігії з номером {@code religion}. */
    public int cell(int religion) {
        return centers.get(religion).cell();
    }

    /** Обертання в порядку кидків — за номером релігії. */
    public List<RollRecord> rolls() {
        return centers.stream().map(HolyCenter::roll).toList();
    }
}
