package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.SourceKind;
import kolo.engine.wheel.RollRecord;

/**
 * Нагорода стріку генерації (GD §4.10) — результат {@link StreakWheel}.
 *
 * <p>Жетони долі й додаткових відомих людей держава отримає, коли ланцюжок коліс збиратиметься в державу: жетони —
 * з обрізанням до ліміту, людей — додатковими обертаннями колеса відомих людей.
 *
 * @param streak вид стріку
 * @param reward нагорода, що випала
 * @param tags мітки колеса стріку разом із мітками нагороди
 * @param roll обертання колеса стріку
 */
public record StreakBonus(StreakKind streak, StreakRewardDef reward, SortedSet<String> tags, RollRecord roll) {

    public StreakBonus {
        Objects.requireNonNull(streak, "streak");
        Objects.requireNonNull(reward, "reward");
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        Objects.requireNonNull(roll, "roll");
    }

    /**
     * Модифікатори нагороди в порядку контенту. Діють {@code duration} років від 01.01.1970: останній хід дії —
     * {@code duration − 1}; {@code 0} — постійно.
     *
     * <p>Id — {@code streak:<вид>:<нагорода>:<номер>}; ключ пояснення — {@code streak.<вид>.<нагорода>}: клієнт
     * показує в ньому назву нагороди з контенту.
     */
    public List<Modifier> modifiers() {
        String refId = streak.key() + ":" + reward.id();
        ModifierSource source = new ModifierSource(SourceKind.STREAK, refId);
        Integer expiresAtTurn = reward.durationYears() == 0 ? null : reward.durationYears() - 1;
        String descriptionKey = "streak." + streak.key() + "." + reward.id();
        List<Modifier> result = new ArrayList<>();
        for (int i = 0; i < reward.modifiers().size(); i++) {
            result.add(reward.modifiers()
                    .get(i)
                    .toModifier("streak:" + refId + ":" + i, source, expiresAtTurn, descriptionKey));
        }
        return List.copyOf(result);
    }

    /** Жетони долі нагороди, до обрізання до ліміту. */
    public int fateTokens() {
        return reward.fateTokens();
    }

    /** Скільки додаткових відомих людей дає нагорода. */
    public int extraPeople() {
        return reward.extraPeople();
    }
}
