package kolo.engine.modifier;

import java.util.Objects;
import kolo.engine.state.Stat;
import kolo.engine.wheel.WheelKind;

/** На що діє модифікатор: показник держави або перевага колеса певного типу. */
public sealed interface ModifierTarget {

    /** Додається до ефективного значення показника, в одиницях показника. */
    record StatTarget(Stat stat) implements ModifierTarget {

        public StatTarget {
            Objects.requireNonNull(stat, "stat");
        }
    }

    /** Додається до переваги колеса цього типу (до обрізання до {@code −100..100}). */
    record WheelTarget(WheelKind kind) implements ModifierTarget {

        public WheelTarget {
            Objects.requireNonNull(kind, "kind");
        }
    }

    static ModifierTarget stat(Stat stat) {
        return new StatTarget(stat);
    }

    static ModifierTarget wheel(WheelKind kind) {
        return new WheelTarget(kind);
    }
}
