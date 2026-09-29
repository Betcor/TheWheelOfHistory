package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kolo.engine.wheel.RollRecord;

/**
 * Відомі люди держави на старті (GD §4.1, колесо 17; GD §4.8) — результат {@link PeopleWheel}.
 *
 * @param people постаті в порядку генерації
 * @param countRoll обертання колеса кількості постатей
 */
public record StartPeople(List<StartPerson> people, RollRecord countRoll) {

    public StartPeople {
        people = List.copyOf(people);
        Objects.requireNonNull(countRoll, "countRoll");
    }

    /** Усі обертання в порядку кидків: кількість постатей, далі обертання кожної постаті. */
    public List<RollRecord> rolls() {
        List<RollRecord> rolls = new ArrayList<>();
        rolls.add(countRoll);
        people.forEach(person -> rolls.addAll(person.rolls()));
        return List.copyOf(rolls);
    }
}
