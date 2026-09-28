package kolo.engine.wheel;

import java.util.List;
import java.util.Objects;

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
        Ids.requireSnakeCase(id, "id сектора");
        if (weightBp < 0 || weightBp > Wheel.TOTAL_BP) {
            throw new IllegalArgumentException("вага сектора " + id + " поза 0..10000: " + weightBp);
        }
        Objects.requireNonNull(value, "value");
        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("якість сектора " + id + " поза 0..100: " + quality);
        }
        Objects.requireNonNull(tier, "tier");
        tags = List.copyOf(tags);
    }

    /** Той самий сектор з іншою вагою. */
    public Sector<T> withWeight(int newWeightBp) {
        return new Sector<>(id, newWeightBp, value, quality, tier, tags);
    }
}
