package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestBackstory.ARMS_RACE;
import static kolo.engine.generation.country.TestBackstory.CIVIL_WAR;
import static kolo.engine.generation.country.TestBackstory.COUNTRY;
import static kolo.engine.generation.country.TestBackstory.FLOOD;
import static kolo.engine.generation.country.TestBackstory.NEIGHBOR;
import static kolo.engine.generation.country.TestBackstory.PACK;
import static kolo.engine.generation.country.TestBackstory.RECONCILIATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.BackstoryText;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.TagCondition;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.state.LocalizedName;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackstoryWheelTest {

    private static final List<CountryId> NEIGHBORS = List.of(CountryId.of(3), CountryId.of(7));
    private static final int SEEDS = 500;

    @Test
    void getsTheRolledNumberOfDistinctFragments() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Backstory backstory = generate(seed, Set.of(), NEIGHBORS);

            RollRecord count = backstory.rolls().getFirst();
            assertThat(count.kind()).isEqualTo(BackstoryWheel.COUNT_KIND);
            assertThat(backstory.entries())
                    .hasSize(Integer.parseInt(count.resultSectorId().substring(10)));
            assertThat(backstory.entries().stream()
                            .map(entry -> entry.fragment().id()))
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void everyFragmentHasItsOwnRoll() {
        Backstory backstory = generate(1, Set.of(), NEIGHBORS);

        List<RollRecord> fragmentRolls =
                backstory.rolls().subList(1, backstory.rolls().size());
        assertThat(fragmentRolls).hasSameSizeAs(backstory.entries());
        for (int i = 0; i < fragmentRolls.size(); i++) {
            RollRecord roll = fragmentRolls.get(i);
            BackstoryFragmentDef fragment = backstory.entries().get(i).fragment();
            assertThat(roll.kind()).isEqualTo(BackstoryWheel.FRAGMENT_KIND);
            assertThat(roll.resultSectorId()).isEqualTo(fragment.id().value());
            assertThat(roll.result().quality()).isEqualTo(fragment.quality());
            assertThat(roll.advantage()).isZero();
            assertThat(roll.turn()).isZero();
            assertThat(roll.season()).isNull();
        }
    }

    @Test
    void yearsAreChronologicalAndWithinFragmentRanges() {
        for (long seed = 0; seed < SEEDS; seed++) {
            int previous = BackstoryFragmentDef.EARLIEST_YEAR;
            for (BackstoryEntry entry : generate(seed, Set.of(), NEIGHBORS).entries()) {
                assertThat(entry.year())
                        .isBetween(entry.fragment().yearFrom(), entry.fragment().yearTo())
                        .isGreaterThanOrEqualTo(previous);
                previous = entry.year();
            }
        }
    }

    @Test
    void fragmentThatEndedBeforeAnEarlierChoiceIsNotOffered() {
        // Пізня подія першою не лишає місця ранній: передісторія закінчується раніше, а не йде назад у часі.
        BackstoryFragmentDef late = TestBackstory.fragment("late", 100, 50, 1969, 1969, List.of());
        BackstoryFragmentDef early = TestBackstory.fragment("early", 100, 50, 1900, 1910, List.of());
        ContentPack pack = TestBackstory.pack(List.of(late, early), new CountRange(2, 2));

        boolean lateFirstSeen = false;
        boolean earlyFirstSeen = false;
        for (long seed = 0; seed < 200; seed++) {
            List<BackstoryEntry> entries = BackstoryWheel.generate(Rng.of(seed), pack, Set.of(), List.of())
                    .entries();
            if (entries.getFirst().fragment().equals(late)) {
                lateFirstSeen = true;
                assertThat(entries).hasSize(1);
            } else {
                earlyFirstSeen = true;
                assertThat(entries).extracting(BackstoryEntry::fragment).containsExactly(early, late);
            }
        }
        assertThat(lateFirstSeen).isTrue();
        assertThat(earlyFirstSeen).isTrue();
    }

    @Test
    void laterFragmentsLeaveRoomForEachOther() {
        // Рік першого обмежений роками другого: без цього перший міг би випасти на 1969 і лишити 1969..1966 порожнім.
        BackstoryFragmentDef wide = TestBackstory.fragment("wide", 10_000, 50, 1900, 1969, List.of("wide"));
        BackstoryFragmentDef narrow = new BackstoryFragmentDef(
                new BackstoryFragmentId("narrow"),
                100,
                50,
                1950,
                1955,
                new TagCondition(List.of("wide"), List.of(), List.of()),
                new TreeMap<>(),
                false,
                List.of(),
                0,
                List.of(),
                new BackstoryText("text", "Подія {year} року."));
        ContentPack pack = TestBackstory.pack(List.of(wide, narrow), new CountRange(2, 2));

        for (long seed = 0; seed < 200; seed++) {
            List<BackstoryEntry> entries = BackstoryWheel.generate(Rng.of(seed), pack, Set.of(), List.of())
                    .entries();

            assertThat(entries).extracting(BackstoryEntry::fragment).containsExactly(wide, narrow);
            assertThat(entries.getFirst().year()).isLessThanOrEqualTo(1955);
        }
    }

    @Test
    void stopsEarlyWhenFragmentsRunOut() {
        ContentPack pack = TestBackstory.pack(List.of(FLOOD), new CountRange(3, 3));

        Backstory backstory = BackstoryWheel.generate(Rng.of(5), pack, Set.of(), List.of());

        assertThat(backstory.entries()).extracting(BackstoryEntry::fragment).containsExactly(FLOOD);
        assertThat(backstory.rolls()).hasSize(2);
    }

    @Test
    void conditionsSeeTagsAddedByEarlierFragments() {
        for (long seed = 0; seed < SEEDS; seed++) {
            List<BackstoryFragmentDef> fragments = generate(seed, Set.of(), NEIGHBORS).entries().stream()
                    .map(BackstoryEntry::fragment)
                    .toList();
            if (fragments.contains(RECONCILIATION)) {
                assertThat(fragments.indexOf(CIVIL_WAR)).isBetween(0, fragments.indexOf(RECONCILIATION) - 1);
            }
        }
    }

    @Test
    void resultTagsAreStartingTagsPlusAddedOnes() {
        Backstory backstory = generate(11, Set.of("democratic"), NEIGHBORS);

        TreeSet<String> expected = new TreeSet<>(Set.of("democratic"));
        backstory.entries().forEach(entry -> expected.addAll(entry.fragment().adds()));
        assertThat(backstory.tags()).containsExactlyElementsOf(expected);
    }

    @Test
    void zeroWeightFragmentIsNeverOffered() {
        for (long seed = 0; seed < SEEDS; seed++) {
            assertThat(generate(seed, Set.of("pacifist"), NEIGHBORS).entries())
                    .extracting(BackstoryEntry::fragment)
                    .doesNotContain(ARMS_RACE);
        }
    }

    @Test
    void withoutCandidatesThereIsNoNeighbor() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Backstory backstory = generate(seed, Set.of(), List.of());

            assertThat(backstory.neighbor()).isEmpty();
            assertThat(backstory.entries()).noneMatch(entry -> entry.fragment().neighbor());
        }
    }

    @Test
    void neighborIsChosenOnceAndOnlyForNeighborFragments() {
        TreeSet<CountryId> seen = new TreeSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            Backstory backstory = generate(seed, Set.of(), NEIGHBORS);
            boolean hasNeighborFragment = backstory.entries().stream()
                    .anyMatch(entry -> entry.fragment().neighbor());

            assertThat(backstory.neighbor().isPresent()).isEqualTo(hasNeighborFragment);
            backstory.neighbor().ifPresent(seen::add);
        }
        assertThat(seen).containsExactlyElementsOf(NEIGHBORS);
    }

    @Test
    void textsRenderWithNamesAndYears() {
        for (long seed = 0; seed < 50; seed++) {
            Backstory backstory = generate(seed, Set.of(), NEIGHBORS);
            Optional<LocalizedName> neighbor = backstory.neighbor().map(id -> NEIGHBOR);

            for (BackstoryEntry entry : backstory.entries()) {
                String text = entry.text(COUNTRY, neighbor);
                assertThat(text).contains(String.valueOf(entry.year())).doesNotContain("{", "}");
                assertThat(text).containsAnyOf("Велорі", "Тормаром");
            }
        }
    }

    @Test
    void countWheelHasEqualSectorsForEveryCount() {
        List<Sector<Integer>> sectors = Wheel.applyAdvantage(BackstoryWheel.countSectors(PACK), 0, Wheel.MAX_STRENGTH);

        assertThat(sectors).extracting(Sector::id).containsExactly("fragments_2", "fragments_3", "fragments_4");
        assertThat(sectors).extracting(Sector::value).containsExactly(2, 3, 4);
        assertThat(sectors).extracting(Sector::weightBp).containsExactly(3334, 3333, 3333);
    }

    @Test
    void fragmentWheelWeightsComeFromTags() {
        List<Sector<BackstoryFragmentDef>> sectors = BackstoryWheel.fragmentSectors(
                PACK, Set.of("junta"), false, Set.of(), BackstoryFragmentDef.EARLIEST_YEAR);

        // Без сусіда й без громадянської війни: 4 фрагменти без умов, arms_race з добавкою за хунту.
        assertThat(sectors)
                .extracting(Sector::id)
                .containsExactly("arms_race", "civil_war", "famine", "flood", "golden_age");
        assertThat(sectors.getFirst().weightBp()).isEqualTo(5100);
        assertThat(sectors).allMatch(sector -> sector.tier() == OutcomeTier.PARTIAL);
        assertThat(sectors)
                .allMatch(sector -> sector.quality() == sector.value().quality());
        assertThat(sectors)
                .allMatch(sector -> sector.tags().equals(sector.value().adds()));
    }

    @ParameterizedTest
    @ValueSource(ints = {-100, 0, 100})
    void advantageDoesNotChangeBackstoryWheels(int advantage) {
        // Сектори передісторії — PARTIAL: навіть гранична перевага не зсуває ваги (зсув коридором сили — пізніше).
        List<Sector<BackstoryFragmentDef>> fragments =
                BackstoryWheel.fragmentSectors(PACK, Set.of(), true, Set.of(), BackstoryFragmentDef.EARLIEST_YEAR);
        List<Sector<Integer>> counts = BackstoryWheel.countSectors(PACK);

        assertThat(weights(Wheel.applyAdvantage(fragments, advantage, Wheel.MAX_STRENGTH)))
                .isEqualTo(weights(Wheel.applyAdvantage(fragments, 0, Wheel.MAX_STRENGTH)));
        assertThat(weights(Wheel.applyAdvantage(counts, advantage, Wheel.MAX_STRENGTH)))
                .isEqualTo(weights(Wheel.applyAdvantage(counts, 0, Wheel.MAX_STRENGTH)));
    }

    @Test
    void rolledSectorsSumToFullWheel() {
        for (RollRecord roll : generate(3, Set.of(), NEIGHBORS).rolls()) {
            assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                    .isEqualTo(Wheel.TOTAL_BP);
        }
    }

    private static Backstory generate(long seed, Set<String> tags, List<CountryId> neighbors) {
        return BackstoryWheel.generate(Rng.of(seed), PACK, tags, neighbors);
    }

    private static <T> List<Integer> weights(List<Sector<T>> sectors) {
        return sectors.stream().map(Sector::weightBp).toList();
    }
}
