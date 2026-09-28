package kolo.engine.modifier;

import kolo.engine.state.Stat;
import kolo.engine.wheel.WheelKind;

/** Тестові модифікатори. */
final class TestModifiers {

    static final WheelKind CONSTRUCTION = new WheelKind("construction");
    static final WheelKind BATTLE = new WheelKind("battle");

    private TestModifiers() {}

    static Modifier onStat(String id, Stat stat, int value, Integer expiresAtTurn) {
        return new Modifier(
                id,
                new ModifierSource(SourceKind.EVENT, "evt_" + id),
                ModifierTarget.stat(stat),
                value,
                expiresAtTurn,
                "modifier." + id);
    }

    static Modifier onWheel(String id, WheelKind kind, int value, Integer expiresAtTurn) {
        return new Modifier(
                id,
                new ModifierSource(SourceKind.BUILDING, "bld_" + id),
                ModifierTarget.wheel(kind),
                value,
                expiresAtTurn,
                "modifier." + id);
    }
}
