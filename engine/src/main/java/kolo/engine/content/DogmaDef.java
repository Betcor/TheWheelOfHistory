package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Догмат — правило віри (GD §25.1, колесо 3): «священна війна», «аскеза», «шана вченим»…
 *
 * @param weight базова вага в колесі догмату, {@code 1..}{@value #MAX_WEIGHT}; відносна
 * @param weightTags добавки до ваги за мітки архетипу, аспектів і вже обраних догматів; можуть бути від'ємні
 * @param modifiers модифікатори держав цієї віри; обмеження догмату (що він забороняє) з'являться разом із системами,
 *     які він обмежує
 * @param tags мітки релігії з цим догматом, напр. {@code dogma_holy_war}
 * @param incompatible догмати, з якими цей не випадає в одній релігії; відношення симетричне, тож досить указати
 *     його з одного боку
 */
public record DogmaDef(
        DogmaId id,
        String name,
        String description,
        int weight,
        SortedMap<String, Integer> weightTags,
        List<ModifierDef> modifiers,
        List<String> tags,
        List<DogmaId> incompatible) {

    /** Найбільша відносна вага догмату. */
    public static final int MAX_WEIGHT = 10_000;

    public DogmaDef {
        Objects.requireNonNull(id, "id");
        String field = "dogma." + id;
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        weightTags = Defs.weightTags(field + ".weight_tags", weightTags, MAX_WEIGHT);
        modifiers = List.copyOf(modifiers);
        tags = Defs.tags(field + ".tags", tags);
        incompatible = List.copyOf(incompatible);
        TreeSet<DogmaId> seen = new TreeSet<>();
        for (DogmaId other : incompatible) {
            if (other.equals(id)) {
                throw new ValidationException(
                        ErrorCode.SELF_REFERENCE, ErrorDetails.of("field", field + ".incompatible"));
            }
            Defs.unique(field + ".incompatible", seen, other);
        }
    }

    /** Вага для релігії з цими мітками: базова плюс добавки, у межах {@code 0..}{@value #MAX_WEIGHT}. */
    public int weightFor(Set<String> tags) {
        return Defs.weightFor(weight, weightTags, tags, MAX_WEIGHT);
    }
}
