package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Ідеологія (GD §4.2).
 *
 * @param name назва українською
 * @param weight вага в колесі ідеології, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує ваги всіх
 *     ідеологій
 * @param modifiers постійні модифікатори держави з цією ідеологією
 * @param tags мітки для подальших генерацій
 * @param subIdeologies підкласифікації в порядку контенту (порядок секторів колеса); хоча б одна
 */
public record IdeologyDef(
        IdeologyId id,
        String name,
        int weight,
        List<ModifierDef> modifiers,
        List<String> tags,
        List<SubIdeologyDef> subIdeologies) {

    /** Найбільша відносна вага ідеології чи підкласифікації в колесі генерації. */
    public static final int MAX_WEIGHT = 10_000;

    public IdeologyDef {
        Objects.requireNonNull(id, "id");
        Checks.notBlank("ideology." + id + ".name", name);
        Checks.inRange("ideology." + id + ".weight", weight, 1, MAX_WEIGHT);
        modifiers = List.copyOf(modifiers);
        tags = Defs.tags("ideology." + id + ".tags", tags);
        subIdeologies = List.copyOf(subIdeologies);
        if (subIdeologies.isEmpty()) {
            throw new ValidationException(
                    ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "ideology." + id + ".sub_ideologies"));
        }
        TreeSet<SubIdeologyId> seen = new TreeSet<>();
        for (SubIdeologyDef sub : subIdeologies) {
            Defs.unique("sub_ideology.id", seen, sub.id());
        }
    }

    /** Підкласифікація цієї ідеології. */
    public Optional<SubIdeologyDef> subIdeology(SubIdeologyId subId) {
        return subIdeologies.stream().filter(sub -> sub.id().equals(subId)).findFirst();
    }
}
