package kolo.engine.modifier;

import java.util.List;
import java.util.Objects;
import kolo.engine.state.Stat;
import kolo.engine.wheel.AppliedModifier;

/**
 * Розклад ефективного значення показника для підказки «звідки це число».
 *
 * @param stat показник
 * @param base базове значення зі стану
 * @param contributions активні модифікатори в порядку списку
 * @param effective {@code base + Σ contributions}, обрізане до меж показника
 */
public record StatBreakdown(Stat stat, long base, List<AppliedModifier> contributions, long effective) {

    public StatBreakdown {
        Objects.requireNonNull(stat, "stat");
        contributions = List.copyOf(contributions);
    }

    /** Чи обрізано суму межами показника. */
    public boolean clamped() {
        long sum = base;
        for (AppliedModifier contribution : contributions) {
            sum += contribution.value();
        }
        return sum != effective;
    }
}
