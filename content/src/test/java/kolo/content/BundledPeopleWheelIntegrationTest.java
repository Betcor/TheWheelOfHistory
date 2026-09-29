package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.generation.country.PeopleWheel;
import kolo.engine.generation.country.StartPeople;
import kolo.engine.generation.country.StartPerson;
import kolo.engine.rng.Rng;
import kolo.engine.state.PersonKind;
import org.junit.jupiter.api.Test;

/** Вбудовані типи, риси й імена ↔ колесо відомих людей рушія. */
class BundledPeopleWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 200;

    @Test
    void everyKindCanAppearWithoutTags() {
        for (PersonKind kind : PersonKind.values()) {
            assertThat(PACK.personKind(kind).weightFor(Set.of())).as(kind.key()).isPositive();
        }
    }

    @Test
    void everyRegimeGetsWellFormedPeopleInEveryStyle() {
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                TreeSet<String> tags = new TreeSet<>(ideology.tags());
                tags.addAll(sub.tags());
                for (NameStyleId style : PACK.names().styles().keySet()) {
                    for (long seed = 0; seed < SEEDS; seed++) {
                        StartPeople people = PeopleWheel.generate(Rng.of(seed), PACK, tags, style, Set.of());

                        assertThat(PACK.balance()
                                        .generation()
                                        .notablePeople()
                                        .contains(people.people().size()))
                                .as(sub.id().value())
                                .isTrue();
                        for (StartPerson person : people.people()) {
                            assertThat(person.traits()).isNotEmpty();
                            assertThat(person.traits())
                                    .allMatch(trait ->
                                            PACK.trait(trait).orElseThrow().allows(person.kind()));
                            assertThat(PACK.balance().generation().personAge().contains(person.age()))
                                    .isTrue();
                        }
                    }
                }
            }
        }
    }

    @Test
    void regimeAndDevelopmentShiftKinds() {
        assertMoreFrequent(PersonKind.PROPHET, Set.of("theocratic"), Set.of("democratic"));
        assertMoreFrequent(PersonKind.GENERAL, Set.of("authoritarian", "junta", "militarism"), Set.of("democratic"));
        assertMoreFrequent(PersonKind.PRETENDER, Set.of("authoritarian", "junta"), Set.of("democratic"));
        assertMoreFrequent(PersonKind.MAGNATE, Set.of("authoritarian", "oligarchy"), Set.of("socialist"));
        assertMoreFrequent(PersonKind.SCIENTIST, Set.of("high_hdi", "advanced"), Set.of("low_hdi", "backward"));
        assertMoreFrequent(PersonKind.ARTIST, Set.of("high_hdi"), Set.of("low_hdi"));
    }

    @Test
    void crowdedWorldStillHasFreeNames() {
        // 40 держав одного стилю по 3 постаті — більше, ніж буде в одному світі.
        NameStyleId style = PACK.names().styles().firstKey();
        TreeSet<String> taken = new TreeSet<>();
        for (long country = 0; country < 40; country++) {
            for (StartPerson person : PeopleWheel.generate(Rng.of(country), PACK, Set.of(), style, taken)
                    .people()) {
                assertThat(taken.add(person.name().fullName().nominative())).isTrue();
            }
        }
    }

    /** Частка типу серед усіх типів для {@code more} більша, ніж для {@code less}. */
    private static void assertMoreFrequent(PersonKind kind, Set<String> more, Set<String> less) {
        assertThat(share(kind, more)).as(kind.key()).isGreaterThan(share(kind, less));
    }

    private static double share(PersonKind kind, Set<String> tags) {
        long total = PACK.personKinds().values().stream()
                .mapToLong(def -> def.weightFor(tags))
                .sum();
        return (double) PACK.personKind(kind).weightFor(tags) / total;
    }
}
