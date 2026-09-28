package kolo.engine.modifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import kolo.engine.state.CountryStats;
import kolo.engine.state.Stat;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.WheelKind;

/**
 * Обчислення ефективних значень з базових і модифікаторів.
 *
 * <p>Порядок внесків — порядок списку модифікаторів (у стані він детермінований). На результат порядок не
 * впливає: сума комутативна, а обрізання робиться один раз, після підсумовування.
 */
public final class Modifiers {

    private Modifiers() {}

    /** Внески активних у ході {@code turn} модифікаторів з цією ціллю, у порядку списку. */
    public static List<AppliedModifier> contributions(List<Modifier> modifiers, ModifierTarget target, int turn) {
        Objects.requireNonNull(target, "target");
        List<AppliedModifier> result = new ArrayList<>();
        for (Modifier modifier : modifiers) {
            if (modifier.target().equals(target) && modifier.isActiveAt(turn)) {
                result.add(new AppliedModifier(modifier.id(), modifier.descriptionKey(), modifier.value()));
            }
        }
        return List.copyOf(result);
    }

    /** Розклад ефективного значення одного показника. */
    public static StatBreakdown breakdown(CountryStats base, List<Modifier> modifiers, Stat stat, int turn) {
        long baseValue = stat.of(base);
        List<AppliedModifier> contributions = contributions(modifiers, ModifierTarget.stat(stat), turn);
        long sum = baseValue;
        for (AppliedModifier contribution : contributions) {
            sum = Math.addExact(sum, contribution.value());
        }
        return new StatBreakdown(stat, baseValue, contributions, stat.clamp(sum));
    }

    /** Ефективні стати: кожен показник — {@code base + Σ модифікаторів}, обрізаний до своїх меж. */
    public static CountryStats effective(CountryStats base, List<Modifier> modifiers, int turn) {
        EnumMap<Stat, Long> values = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            values.put(stat, breakdown(base, modifiers, stat, turn).effective());
        }
        return CountryStats.from(values::get);
    }

    /**
     * Перевага колеса від модифікаторів з ціллю {@code kind}. Інші внески (стати, вкладення, місцевість) система
     * додає сама, об'єднуючи {@link #contributions} зі своїми перед {@link Advantage#of}.
     */
    public static Advantage advantage(List<Modifier> modifiers, WheelKind kind, int turn) {
        return Advantage.of(contributions(modifiers, ModifierTarget.wheel(kind), turn));
    }

    /**
     * Модифікатори, що діятимуть і після ходу {@code turn}: викликається в кінці ходу, щоб зняти ті, в яких
     * {@code expiresAtTurn <= turn}. Порядок решти зберігається.
     */
    public static List<Modifier> withoutExpired(List<Modifier> modifiers, int turn) {
        List<Modifier> result = new ArrayList<>(modifiers.size());
        for (Modifier modifier : modifiers) {
            if (modifier.isActiveAt(turn + 1)) {
                result.add(modifier);
            }
        }
        return List.copyOf(result);
    }
}
