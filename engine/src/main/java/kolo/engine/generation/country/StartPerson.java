package kolo.engine.generation.country;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.GenerationBalanceDef;
import kolo.engine.content.TraitId;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.PersonKind;
import kolo.engine.state.Sex;
import kolo.engine.wheel.RollRecord;

/**
 * Відома постать на старті (GD §4.8) — частина результату {@link PeopleWheel}. Посаду, лояльність і id постаті
 * призначить держава разом із системою людей.
 *
 * @param name ім'я в усіх відмінках; рід відповідає статі
 * @param traits риси в порядку вибору, без повторів, {@code 1..}{@value GenerationBalanceDef#MAX_TRAITS}
 * @param bornTurn хід народження: {@code 0} — 1970 рік, вік на старті — {@code −bornTurn}
 * @param rolls обертання в порядку кидків: тип, кількість рис, далі по одному на рису
 */
public record StartPerson(
        PersonKind kind, Sex sex, LocalizedName name, List<TraitId> traits, int bornTurn, List<RollRecord> rolls) {

    public StartPerson {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sex, "sex");
        Objects.requireNonNull(name, "name");
        if (name.fullName().gender() != sex.gender()) {
            throw new ValidationException(
                    ErrorCode.NAME_GENDER_MISMATCH,
                    ErrorDetails.of(
                            "field",
                            "person.name",
                            "value",
                            name.fullName().nominative(),
                            "expected",
                            sex.gender().key()));
        }
        traits = List.copyOf(traits);
        Checks.inRange("person.traits", traits.size(), 1, GenerationBalanceDef.MAX_TRAITS);
        TreeSet<TraitId> seen = new TreeSet<>();
        for (TraitId trait : traits) {
            if (!seen.add(trait)) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", "person.traits", "value", trait));
            }
        }
        Checks.inRange(
                "person.born_turn",
                bornTurn,
                -GenerationBalanceDef.MAX_PERSON_AGE,
                -GenerationBalanceDef.MIN_PERSON_AGE);
        rolls = List.copyOf(rolls);
    }

    /** Вік на 1970 рік. */
    public int age() {
        return -bornTurn;
    }
}
