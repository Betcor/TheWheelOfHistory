package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.BalanceDef;
import kolo.engine.content.MedianRange;
import kolo.engine.content.PowerBudgetDef;
import kolo.engine.content.PowerComponent;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.wheel.WheelKind;

/**
 * Бюджет сили держави при генерації (GD §4.11): незмінний підсумок уже обернених складників.
 *
 * <p>Після кожного складника ({@link #add}) сила перераховується за правилами {@link PowerBudgetDef}. Якщо вона
 * поза коридором хоста ({@link #range()}), наступні складники отримують зсув до середини — модифікатор переваги
 * {@link #modifiers(List)} з поясненням {@value #DESCRIPTION_KEY}. Зсув не стає модифікатором держави: він діє лише
 * на колеса генерації.
 *
 * @param corridor коридор, який задав хост (GD §3.4)
 * @param npc чи держава NPC: для NPC коридор ширший
 * @param steps обернені складники в порядку ланцюжка, кожен — щонайбільше раз ({@link ErrorCode#DUPLICATE_ID})
 */
public record PowerBudget(PowerCorridor corridor, boolean npc, List<PowerStep> steps) {

    /** Ключ пояснення зсуву в записі колеса. */
    public static final String DESCRIPTION_KEY = "power_corridor";

    /** Сила держави без жодного складника: медіана. */
    public static final int MEDIAN_PCT = 100;

    public PowerBudget {
        Objects.requireNonNull(corridor, "corridor");
        steps = List.copyOf(steps);
        TreeSet<PowerComponent> seen = new TreeSet<>();
        for (PowerStep step : steps) {
            if (!seen.add(step.component())) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID,
                        ErrorDetails.of(
                                "field",
                                "power_budget.steps",
                                "value",
                                step.component().key()));
            }
        }
    }

    /** Бюджет до першого складника. */
    public static PowerBudget start(PowerCorridor corridor, boolean npc) {
        return new PowerBudget(corridor, npc, List.of());
    }

    /** Межі коридору цієї держави. */
    public MedianRange range(BalanceDef balance) {
        return balance.corridor(corridor).range(npc);
    }

    /**
     * Бюджет після ще одного складника.
     *
     * @param quality якість результату складника, {@code 0..100}
     */
    public PowerBudget add(BalanceDef balance, PowerComponent component, int quality) {
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(component, "component");
        PowerBudgetDef rules = balance.power();
        long weighted = (long) rules.weight(component) * quality;
        long weights = rules.weight(component);
        for (PowerStep step : steps) {
            weighted += (long) rules.weight(step.component()) * step.quality();
            weights += rules.weight(step.component());
        }
        int strength =
                weights == 0 ? MEDIAN_PCT : (int) Math.floorDiv(weighted * MEDIAN_PCT, weights * rules.medianQuality());
        List<PowerStep> next = new ArrayList<>(steps);
        next.add(new PowerStep(component, quality, strength, rules.advantage(strength, range(balance))));
        return new PowerBudget(corridor, npc, next);
    }

    /** Проміжна сила, % медіани; до першого складника — {@value #MEDIAN_PCT}. */
    public int strengthPct() {
        return steps.isEmpty() ? MEDIAN_PCT : steps.getLast().strengthPct();
    }

    /** Зсув наступних складників; до першого складника — нуль. */
    public int advantage() {
        return steps.isEmpty() ? 0 : steps.getLast().advantage();
    }

    /**
     * Зсув як модифікатори переваги коліс {@code kinds} (id {@code power_corridor:<тип колеса>}); у коридорі —
     * порожньо.
     */
    public List<Modifier> modifiers(List<WheelKind> kinds) {
        int advantage = advantage();
        if (advantage == 0) {
            return List.of();
        }
        ModifierSource source = new ModifierSource(SourceKind.POWER_BUDGET, corridor.key());
        return kinds.stream()
                .map(kind -> new Modifier(
                        DESCRIPTION_KEY + ":" + kind.id(),
                        source,
                        ModifierTarget.wheel(kind),
                        advantage,
                        null,
                        DESCRIPTION_KEY))
                .toList();
    }
}
