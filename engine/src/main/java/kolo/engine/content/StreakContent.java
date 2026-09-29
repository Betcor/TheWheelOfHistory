package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/** Колеса стріків генерації (GD §4.10): рівно по одному на кожен {@link StreakKind}. */
public final class StreakContent {

    private final SortedMap<StreakKind, StreakWheelDef> wheels;

    /**
     * @throws ValidationException якщо вид колеса повторюється ({@link ErrorCode#DUPLICATE_ID}) чи якогось бракує
     *     ({@link ErrorCode#MISSING_DEFINITION})
     */
    public StreakContent(List<StreakWheelDef> wheels) {
        TreeMap<StreakKind, StreakWheelDef> byKind = new TreeMap<>();
        for (StreakWheelDef wheel : wheels) {
            Objects.requireNonNull(wheel, "streaks");
            if (byKind.putIfAbsent(wheel.kind(), wheel) != null) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID,
                        ErrorDetails.of(
                                "field", "streaks", "value", wheel.kind().key()));
            }
        }
        this.wheels = Defs.complete("streaks", byKind, List.of(StreakKind.values()), StreakKind::key);
    }

    /** Колеса в порядку enum; визначено кожне. */
    public SortedMap<StreakKind, StreakWheelDef> wheels() {
        return wheels;
    }

    public StreakWheelDef wheel(StreakKind kind) {
        return wheels.get(Objects.requireNonNull(kind, "kind"));
    }

    /** Мітки, які можуть дати колеса стріків: мітки коліс і нагород. */
    public SortedSet<String> producedTags() {
        TreeSet<String> produced = new TreeSet<>();
        for (StreakWheelDef wheel : wheels.values()) {
            produced.addAll(wheel.tags());
            wheel.rewards().forEach(reward -> produced.addAll(reward.tags()));
        }
        return Collections.unmodifiableSortedSet(produced);
    }
}
