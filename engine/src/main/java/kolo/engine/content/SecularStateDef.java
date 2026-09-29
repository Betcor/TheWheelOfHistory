package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.error.Checks;

/**
 * Сектор «світська держава» колеса релігії держави (GD §4.1, 6а; §25.2): держава без державної релігії.
 *
 * <p>Колесо крутиться одразу після ладу, тож і вага, і умова залежать лише від міток ідеології й підкласифікації.
 *
 * @param name назва сектору українською
 * @param description опис для гравця
 * @param weight базова вага, {@code 1..}{@value #MAX_WEIGHT}; відносна — порівнюється з вагою релігій світу
 * @param weightTags добавки до ваги за мітки ладу; можуть бути від'ємні
 * @param condition коли держава може бути світською, напр. не теократія
 * @param tags мітки, які отримує світська держава, напр. {@code secular}
 */
public record SecularStateDef(
        String name,
        String description,
        int weight,
        SortedMap<String, Integer> weightTags,
        TagCondition condition,
        List<String> tags) {

    public static final int MAX_WEIGHT = 10_000;

    public SecularStateDef {
        String field = "state_religion.secular";
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        weightTags = Defs.weightTags(field + ".weight_tags", weightTags, MAX_WEIGHT);
        Objects.requireNonNull(condition, "condition");
        tags = Defs.tags(field + ".tags", tags);
    }

    /** Вага для держави з цими мітками: {@code 0}, якщо умова не виконана, інакше базова плюс добавки. */
    public int weightFor(Set<String> tags) {
        if (!condition.matches(tags)) {
            return 0;
        }
        return Defs.weightFor(weight, weightTags, tags, MAX_WEIGHT);
    }

    /** Мітки держави, від яких залежить сектор: добавки й умова. */
    public SortedSet<String> referencedTags() {
        TreeSet<String> referenced = new TreeSet<>(weightTags.keySet());
        referenced.addAll(condition.tags());
        return Collections.unmodifiableSortedSet(referenced);
    }
}
