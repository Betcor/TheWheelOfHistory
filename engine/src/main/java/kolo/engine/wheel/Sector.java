package kolo.engine.wheel;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Сектор колеса.
 *
 * @param id ключ сектора ({@code snake_case}), унікальний у межах колеса; за ним же розв'язуються рівності при
 *     нормалізації
 * @param weightBp вага в базисних пунктах, {@code 0..10 000}; сума ваг колеса нормалізується до 10 000
 * @param value результат, який отримує система рушія
 * @param quality наскільки результат добрий для держави, {@code 0..100} (стріки, хроніка)
 * @param tier рівень результату; визначає, як на сектор діє перевага
 * @param tags мітки для зв'язності подальших генерацій
 */
public record Sector<T>(String id, int weightBp, T value, int quality, OutcomeTier tier, List<String> tags) {

    public Sector {
        Checks.snakeCase("sector.id", id);
        Checks.inRange("sector." + id + ".weight_bp", weightBp, 0, Wheel.TOTAL_BP);
        Objects.requireNonNull(value, "value");
        Checks.inRange("sector." + id + ".quality", quality, 0, 100);
        Objects.requireNonNull(tier, "tier");
        tags = List.copyOf(tags);
    }

    /** Той самий сектор з іншою вагою. */
    public Sector<T> withWeight(int newWeightBp) {
        return new Sector<>(id, newWeightBp, value, quality, tier, tags);
    }
}
