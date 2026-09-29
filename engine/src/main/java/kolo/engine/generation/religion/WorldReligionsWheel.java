package kolo.engine.generation.religion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Релігії світу на старті (GD §25.1): генеруються до держав. Колесо {@link #COUNT_KIND} з рівними секторами
 * {@code religions_<n>} обирає кількість у межах рядка таблиці {@code religion.count} для кількості держав, далі
 * кожна релігія — ланцюжком {@link ReligionWheel}. Повні назви вір у світі різні.
 */
public final class WorldReligionsWheel {

    public static final WheelKind COUNT_KIND = new WheelKind("generation_religion_count");

    private WorldReligionsWheel() {}

    /**
     * @param rng окремий потік релігій світу; всередині розгалужується на кількість і на кожну релігію
     * @param countries скільки держав буде у світі, {@code ≥ 1}
     * @throws ValidationException якщо {@code countries < 1}
     * @throws InvariantViolationException див. {@link ReligionWheel#generate}
     */
    public static StartReligions generate(Rng rng, ContentPack content, int countries) {
        Objects.requireNonNull(rng, "rng");
        WheelSpin<Integer> countSpin = ReligionWheel.spin(
                content,
                rng.fork("count"),
                COUNT_KIND,
                ReligionWheel.countSectors(
                        "religions_", content.balance().religion().religions(countries)));
        TreeSet<String> taken = new TreeSet<>();
        List<StartReligion> religions = new ArrayList<>();
        for (int i = 0; i < countSpin.value(); i++) {
            StartReligion religion = ReligionWheel.generate(rng.fork("religion:" + i), content, taken);
            taken.add(religion.name().nominative());
            religions.add(religion);
        }
        return new StartReligions(religions, countSpin.record());
    }
}
