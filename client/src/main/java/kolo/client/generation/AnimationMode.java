package kolo.client.generation;

import java.util.Locale;
import kolo.engine.wheel.WheelKind;

/** Які колеса крутяться з анімацією (GD §2.5): усі, лише ключові чи жодне. */
public enum AnimationMode {
    ALL,
    KEY,
    NONE;

    /** Чи крутити колесо цього типу з анімацією; інакше результат з'являється одразу. */
    public boolean animates(WheelKind kind) {
        return switch (this) {
            case ALL -> true;
            case KEY -> GenerationPlan.isKey(kind);
            case NONE -> false;
        };
    }

    /** Ключ тексту: {@code generation.mode.<ключ>}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
