package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.Stat;
import kolo.engine.wheel.Advantage;

/**
 * Шаблон модифікатора в контенті: на що діє й наскільки. Джерело, id і термін дії відомі лише тоді, коли рушій
 * видає модифікатор державі ({@link #toModifier}).
 *
 * @param target показник або тип колеса
 * @param value внесок; для колеса — у межах переваги {@code −100..100}, для показника — не більше ширини його
 *     діапазону в кожен бік
 */
public record ModifierDef(ModifierTarget target, int value) {

    public ModifierDef {
        Objects.requireNonNull(target, "target");
        switch (target) {
            case ModifierTarget.StatTarget(Stat stat) -> {
                long span = Math.min(stat.max() - stat.min(), Integer.MAX_VALUE);
                Checks.inRange("modifier.value", value, -span, span);
            }
            case ModifierTarget.WheelTarget ignored ->
                Checks.inRange("modifier.value", value, Advantage.MIN, Advantage.MAX);
        }
    }

    /** Модифікатор для держави з цього шаблону. */
    public Modifier toModifier(String id, ModifierSource source, Integer expiresAtTurn, String descriptionKey) {
        return new Modifier(id, source, target, value, expiresAtTurn, descriptionKey);
    }
}
