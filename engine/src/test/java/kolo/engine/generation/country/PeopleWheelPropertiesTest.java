package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestPeople.PACK;
import static kolo.engine.generation.country.TestPeople.STYLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.TraitId;
import kolo.engine.rng.Rng;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Властивості колеса відомих людей на довільних seed, мітках і зайнятих іменах. */
class PeopleWheelPropertiesTest {

    @Property
    void sameSeedGivesSamePeople(@ForAll long seed, @ForAll("tags") Set<String> tags) {
        assertThat(PeopleWheel.generate(Rng.of(seed), PACK, tags, STYLE, Set.of()))
                .isEqualTo(PeopleWheel.generate(Rng.of(seed), PACK, tags, STYLE, Set.of()));
    }

    @Property
    void peopleRespectBalanceContentAndEachOther(
            @ForAll long seed, @ForAll("tags") Set<String> tags, @ForAll("taken") Set<String> taken) {
        StartPeople people = PeopleWheel.generate(Rng.of(seed), PACK, tags, STYLE, taken);

        assertThat(TestPeople.PEOPLE.contains(people.people().size())).isTrue();
        TreeSet<String> names = new TreeSet<>(taken);
        for (StartPerson person : people.people()) {
            assertThat(PACK.personKind(person.kind()).weightFor(tags)).isPositive();
            int rolled = Integer.parseInt(person.rolls().get(1).resultSectorId().substring("traits_".length()));
            assertThat(TestPeople.TRAIT_COUNT.contains(rolled)).isTrue();
            // Рис може забракнути, але хоча б одна доступна кожному типу.
            assertThat(person.traits().size()).isBetween(1, rolled);
            List<TraitId> seen = new ArrayList<>();
            for (TraitId trait : person.traits()) {
                assertThat(PACK.trait(trait).orElseThrow().allows(person.kind()))
                        .isTrue();
                assertThat(seen).allMatch(other -> PACK.compatible(trait, other));
                seen.add(trait);
            }
            assertThat(TestPeople.AGE.contains(person.age())).isTrue();
            assertThat(names.add(person.name().fullName().nominative())).isTrue();
        }
    }

    @Property
    void streamsAreIndependentOfTakenNames(@ForAll long seed, @ForAll("taken") Set<String> taken) {
        // Зайняте ім'я змінює лише ім'я: тип, стать, вік і риси беруться з інших потоків.
        List<StartPerson> free = PeopleWheel.generate(Rng.of(seed), PACK, Set.of(), STYLE, Set.of())
                .people();
        List<StartPerson> busy =
                PeopleWheel.generate(Rng.of(seed), PACK, Set.of(), STYLE, taken).people();

        assertThat(busy).hasSameSizeAs(free);
        for (int i = 0; i < free.size(); i++) {
            assertThat(busy.get(i).kind()).isEqualTo(free.get(i).kind());
            assertThat(busy.get(i).sex()).isEqualTo(free.get(i).sex());
            assertThat(busy.get(i).bornTurn()).isEqualTo(free.get(i).bornTurn());
            assertThat(busy.get(i).traits()).isEqualTo(free.get(i).traits());
        }
    }

    @Provide
    Arbitrary<Set<String>> tags() {
        return Arbitraries.of("democratic", "junta", "pacifist", "civil_war").set();
    }

    /** Кілька імен з тих, що дає тестовий стиль. */
    @Provide
    Arbitrary<Set<String>> taken() {
        return Arbitraries.of("Велор Торвер", "Тормор Гальмер", "Салена Маркера", "Марена Торвера", "Велор Маркер")
                .set();
    }
}
