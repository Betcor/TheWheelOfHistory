package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestPeople.CHARISMATIC;
import static kolo.engine.generation.country.TestPeople.GENIUS;
import static kolo.engine.generation.country.TestPeople.LOYAL;
import static kolo.engine.generation.country.TestPeople.PACK;
import static kolo.engine.generation.country.TestPeople.STYLE;
import static kolo.engine.generation.country.TestPeople.TACTICIAN;
import static kolo.engine.generation.country.TestPeople.TREACHEROUS;
import static kolo.engine.generation.country.TestPeople.WEIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.PersonKindDef;
import kolo.engine.content.TraitId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.PersonKind;
import kolo.engine.state.Sex;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PeopleWheelTest {

    @Test
    void sameSeedGivesSamePeople() {
        assertThat(generate(42, Set.of("junta"))).isEqualTo(generate(42, Set.of("junta")));
    }

    @Test
    void peopleCountFollowsCountRoll() {
        for (long seed = 0; seed < 50; seed++) {
            StartPeople people = generate(seed, Set.of());

            assertThat(people.countRoll().kind()).isEqualTo(PeopleWheel.COUNT_KIND);
            assertThat(people.countRoll().resultSectorId())
                    .isEqualTo("people_" + people.people().size());
            assertThat(TestPeople.PEOPLE.contains(people.people().size())).isTrue();
        }
    }

    @Test
    void rollsGoInOrderOfThrows() {
        StartPeople people = generate(7, Set.of());

        List<RollRecord> expected = new ArrayList<>();
        expected.add(people.countRoll());
        people.people().forEach(person -> expected.addAll(person.rolls()));
        assertThat(people.rolls()).containsExactlyElementsOf(expected);
        for (StartPerson person : people.people()) {
            assertThat(person.rolls().get(0).kind()).isEqualTo(PeopleWheel.KIND_KIND);
            assertThat(person.rolls().get(0).resultSectorId())
                    .isEqualTo(person.kind().key());
            assertThat(person.rolls().get(1).kind()).isEqualTo(PeopleWheel.TRAIT_COUNT_KIND);
            assertThat(person.rolls().subList(2, person.rolls().size()))
                    .extracting(RollRecord::resultSectorId)
                    .containsExactlyElementsOf(
                            person.traits().stream().map(TraitId::value).toList());
        }
    }

    @Test
    void wheelsHaveNoAdvantage() {
        for (RollRecord roll : generate(3, Set.of("junta")).rolls()) {
            assertThat(roll.advantage()).isZero();
            assertThat(roll.sectors()).allMatch(sector -> sector.tier() == OutcomeTier.PARTIAL);
            assertThat(roll.sectors()).allMatch(sector -> sector.quality() == PeopleWheel.QUALITY);
            assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                    .isEqualTo(Wheel.TOTAL_BP);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-100, 0, 100})
    void advantageDoesNotChangePeopleWheels(int advantage) {
        // Сектори постатей — PARTIAL: навіть гранична перевага не зсуває ваги.
        assertSameWeights(PeopleWheel.kindSectors(PACK, Set.of("junta")), advantage);
        assertSameWeights(PeopleWheel.countSectors("people_", TestPeople.PEOPLE), advantage);
        assertSameWeights(PeopleWheel.traitSectors(PACK, PersonKind.GENERAL, List.of()), advantage);
    }

    @Test
    void countSectorsAreEqualForEveryCountInRange() {
        List<Sector<Integer>> sectors = PeopleWheel.countSectors("traits_", new CountRange(1, 3));

        assertThat(sectors).extracting(Sector::id).containsExactly("traits_1", "traits_2", "traits_3");
        assertThat(sectors).extracting(Sector::value).containsExactly(1, 2, 3);
        assertThat(sectors).allMatch(sector -> sector.weightBp() == 1);
    }

    @Test
    void kindWeightsFollowStateTags() {
        assertThat(weightOf(PeopleWheel.kindSectors(PACK, Set.of()), PersonKind.GENERAL))
                .isEqualTo(WEIGHT);
        assertThat(weightOf(PeopleWheel.kindSectors(PACK, Set.of("junta")), PersonKind.GENERAL))
                .isEqualTo(WEIGHT + TestPeople.JUNTA_BONUS);
        assertThat(weightOf(PeopleWheel.kindSectors(PACK, Set.of("pacifist")), PersonKind.SCIENTIST))
                .isEqualTo(WEIGHT + TestPeople.PACIFIST_SCIENTIST);
    }

    @Test
    void kindWithZeroWeightIsLeftOut() {
        List<Sector<PersonKind>> sectors = PeopleWheel.kindSectors(PACK, Set.of("pacifist"));

        assertThat(sectors).extracting(Sector::value).doesNotContain(PersonKind.GENERAL);
        assertThat(sectors).hasSize(PersonKind.values().length - 1);
        assertThat(sectors)
                .extracting(Sector::id)
                .containsExactlyElementsOf(Arrays.stream(PersonKind.values())
                        .filter(kind -> kind != PersonKind.GENERAL)
                        .map(PersonKind::key)
                        .toList());
        for (long seed = 0; seed < 200; seed++) {
            assertThat(generate(seed, Set.of("pacifist")).people())
                    .noneMatch(person -> person.kind() == PersonKind.GENERAL);
        }
    }

    @Test
    void withoutAnyAvailableKindThereAreNoPeople() {
        List<PersonKindDef> kinds = Arrays.stream(PersonKind.values())
                .map(kind ->
                        new PersonKindDef(kind, "Тип", "Опис", 1, new TreeMap<>(Map.of("pacifist", -1)), List.of()))
                .toList();
        ContentPack pack = TestPeople.pack(new TestBackstory.People(
                kinds,
                TestPeople.TRAITS,
                TestPeople.PEOPLE,
                TestPeople.TRAIT_COUNT,
                TestPeople.AGE,
                List.of("вел"),
                List.of("торв")));

        StartPeople people = PeopleWheel.generate(Rng.of(1), pack, Set.of("pacifist"), STYLE, Set.of());

        assertThat(people.people()).isEmpty();
        assertThat(people.rolls()).containsExactly(people.countRoll());
    }

    @Test
    void traitSectorsAreAllowedForKindAndCompatible() {
        assertThat(traitIds(PeopleWheel.traitSectors(PACK, PersonKind.SCIENTIST, List.of())))
                .containsExactly(CHARISMATIC, GENIUS, LOYAL, TREACHEROUS);
        assertThat(traitIds(PeopleWheel.traitSectors(PACK, PersonKind.GENERAL, List.of())))
                .containsExactly(CHARISMATIC, LOYAL, TACTICIAN, TREACHEROUS);
        // Обрана риса й несумісна з нею (в обидва боки) вибувають.
        assertThat(traitIds(PeopleWheel.traitSectors(PACK, PersonKind.GENERAL, List.of(LOYAL))))
                .containsExactly(CHARISMATIC, TACTICIAN);
        assertThat(traitIds(PeopleWheel.traitSectors(PACK, PersonKind.GENERAL, List.of(TREACHEROUS))))
                .containsExactly(CHARISMATIC, TACTICIAN);
        assertThat(PeopleWheel.traitSectors(PACK, PersonKind.GENERAL, List.of()))
                .allMatch(sector -> sector.weightBp() == 1
                        && sector.id().equals(sector.value().value()));
    }

    @Test
    void traitsRunOutBeforeCountIsReached() {
        // Пророкові доступні лише загальні риси, і відданий з підступним несумісні: найбільше дві риси.
        CountRange three = new CountRange(3, 3);
        ContentPack pack = TestPeople.pack(new TestBackstory.People(
                TestPeople.kinds(),
                TestPeople.TRAITS.stream()
                        .filter(trait -> !trait.id().equals(CHARISMATIC))
                        .toList(),
                TestPeople.PEOPLE,
                three,
                TestPeople.AGE,
                List.of("вел", "тор", "сал"),
                List.of("торв", "гальм")));

        for (long seed = 0; seed < 100; seed++) {
            for (StartPerson person : PeopleWheel.generate(Rng.of(seed), pack, Set.of(), STYLE, Set.of())
                    .people()) {
                assertThat(person.rolls().get(1).resultSectorId()).isEqualTo("traits_3");
                assertThat(person.traits()).hasSize(person.rolls().size() - 2);
                if (!person.kind().equals(PersonKind.SCIENTIST)
                        && !person.kind().equals(PersonKind.GENERAL)
                        && !person.kind().equals(PersonKind.ADMIRAL)) {
                    assertThat(person.traits()).hasSize(1);
                }
            }
        }
    }

    @Test
    void personHasAgeSexAndMatchingName() {
        for (long seed = 0; seed < 100; seed++) {
            for (StartPerson person : generate(seed, Set.of()).people()) {
                assertThat(person.age()).isBetween(TestPeople.AGE.min(), TestPeople.AGE.max());
                assertThat(person.bornTurn()).isEqualTo(-person.age());
                assertThat(person.name().fullName().gender())
                        .isEqualTo(person.sex().gender());
                assertThat(person.name().fullName().nominative())
                        .endsWith(person.name().shortName().nominative());
            }
        }
    }

    @Test
    void namesAreUniqueAndAvoidTakenOnes() {
        // Сал + ор / ен і торв + ер: «Салор Торвер» і «Салена Торвера» — одне ім'я на стать.
        ContentPack pack = TestPeople.pack(new CountRange(1, 1), List.of("сал", "вел"), List.of("торв"));
        Set<String> taken = Set.of("Салор Торвер", "Салена Торвера");

        for (long seed = 0; seed < 50; seed++) {
            StartPerson person = PeopleWheel.generate(Rng.of(seed), pack, Set.of(), STYLE, taken)
                    .people()
                    .getFirst();

            assertThat(person.name().fullName().form(GrammaticalCase.NOMINATIVE))
                    .isIn("Велор Торвер", "Велена Торвера");
        }
        for (long seed = 0; seed < 50; seed++) {
            List<String> names = generate(seed, Set.of()).people().stream()
                    .map(person -> person.name().fullName().nominative())
                    .toList();
            assertThat(new TreeSet<>(names)).hasSameSizeAs(names);
        }
    }

    @Test
    void exhaustedNamesAreAnInvariantViolation() {
        // Одне ім'я на стать і три постаті: двоє тієї самої статі неминучі.
        ContentPack pack = TestPeople.pack(new CountRange(3, 3), List.of("вел"), List.of("торв"));

        assertThatThrownBy(() -> PeopleWheel.generate(Rng.of(1), pack, Set.of(), STYLE, Set.of()))
                .isInstanceOf(InvariantViolationException.class);
    }

    @Test
    void unknownStyleIsRejected() {
        assertThatThrownBy(() -> PeopleWheel.generate(Rng.of(1), PACK, Set.of(), new NameStyleId("eastern"), Set.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    @Test
    void personRejectsInconsistentFields() {
        StartPerson person = generate(5, Set.of()).people().getFirst();
        Sex other = person.sex() == Sex.MALE ? Sex.FEMALE : Sex.MALE;

        assertThatThrownBy(() -> new StartPerson(
                        person.kind(), other, person.name(), person.traits(), person.bornTurn(), person.rolls()))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.NAME_GENDER_MISMATCH));
        assertThatThrownBy(() -> new StartPerson(
                        person.kind(), person.sex(), person.name(), List.of(), person.bornTurn(), person.rolls()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new StartPerson(
                        person.kind(),
                        person.sex(),
                        person.name(),
                        List.of(LOYAL, LOYAL),
                        person.bornTurn(),
                        person.rolls()))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.DUPLICATE_ID));
        assertThatThrownBy(() ->
                        new StartPerson(person.kind(), person.sex(), person.name(), person.traits(), -10, List.of()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void extraPeopleFollowCountedOnesWithoutShiftingThem() {
        for (long seed = 0; seed < 200; seed++) {
            StartPeople base = generate(seed, Set.of());
            StartPeople extended = PeopleWheel.generate(Rng.of(seed), PACK, Set.of(), STYLE, Set.of(), 2);

            assertThat(extended.countRoll()).isEqualTo(base.countRoll());
            assertThat(extended.people()).hasSize(base.people().size() + 2);
            assertThat(extended.people().subList(0, base.people().size())).isEqualTo(base.people());
            assertThat(extended.people())
                    .extracting(person -> person.name().fullName().nominative())
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void negativeExtraPeopleAreRejected() {
        assertThatThrownBy(() -> PeopleWheel.generate(Rng.of(1), PACK, Set.of(), STYLE, Set.of(), -1))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    private static StartPeople generate(long seed, Set<String> tags) {
        return PeopleWheel.generate(Rng.of(seed), PACK, tags, STYLE, Set.of());
    }

    private static <T> void assertSameWeights(List<Sector<T>> sectors, int advantage) {
        assertThat(weights(Wheel.applyAdvantage(sectors, advantage, Wheel.MAX_STRENGTH)))
                .isEqualTo(weights(Wheel.applyAdvantage(sectors, 0, Wheel.MAX_STRENGTH)));
    }

    private static <T> List<Integer> weights(List<Sector<T>> sectors) {
        return sectors.stream().map(Sector::weightBp).toList();
    }

    private static int weightOf(List<Sector<PersonKind>> sectors, PersonKind kind) {
        return sectors.stream()
                .filter(sector -> sector.value() == kind)
                .findFirst()
                .orElseThrow()
                .weightBp();
    }

    private static List<TraitId> traitIds(List<Sector<TraitId>> sectors) {
        return sectors.stream().map(Sector::value).toList();
    }
}
