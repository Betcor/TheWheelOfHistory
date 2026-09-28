package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.PersonKind;

/**
 * Риса відомої постаті (GD §12.3): баф чи дебаф.
 *
 * @param name назва українською
 * @param kinds типи постатей, яким доступна риса; порожній список — будь-якому типу
 * @param modifiers модифікатори від риси; коли й на що вони діють (держава, фронт, НДІ), визначить система людей
 * @param tags мітки для генерації й подій; {@code positive}/{@code negative} — баф чи дебаф
 * @param incompatible риси, з якими ця не поєднується в одній постаті; відношення симетричне, тож досить указати
 *     його з одного боку
 */
public record TraitDef(
        TraitId id,
        String name,
        List<PersonKind> kinds,
        List<ModifierDef> modifiers,
        List<String> tags,
        List<TraitId> incompatible) {

    public TraitDef {
        Objects.requireNonNull(id, "id");
        Checks.notBlank("trait." + id + ".name", name);
        kinds = List.copyOf(kinds);
        TreeSet<PersonKind> seenKinds = new TreeSet<>();
        for (PersonKind kind : kinds) {
            if (!seenKinds.add(kind)) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID,
                        ErrorDetails.of("field", "trait." + id + ".kinds", "value", kind.key()));
            }
        }
        modifiers = List.copyOf(modifiers);
        tags = Defs.tags("trait." + id + ".tags", tags);
        incompatible = List.copyOf(incompatible);
        TreeSet<TraitId> seen = new TreeSet<>();
        for (TraitId other : incompatible) {
            if (other.equals(id)) {
                throw new ValidationException(
                        ErrorCode.SELF_REFERENCE, ErrorDetails.of("field", "trait." + id + ".incompatible"));
            }
            Defs.unique("trait." + id + ".incompatible", seen, other);
        }
    }

    /** Чи може постать цього типу мати рису. */
    public boolean allows(PersonKind kind) {
        Objects.requireNonNull(kind, "kind");
        return kinds.isEmpty() || kinds.contains(kind);
    }
}
