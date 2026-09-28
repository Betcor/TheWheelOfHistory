package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestBackstory.COUNT;
import static kolo.engine.generation.country.TestBackstory.PACK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Властивості колеса передісторії на довільних seed, мітках і сусідах. */
class BackstoryWheelPropertiesTest {

    @Property
    void sameSeedGivesSameBackstory(
            @ForAll long seed, @ForAll("tags") Set<String> tags, @ForAll("neighbors") List<CountryId> neighbors) {
        assertThat(BackstoryWheel.generate(Rng.of(seed), PACK, tags, neighbors))
                .isEqualTo(BackstoryWheel.generate(Rng.of(seed), PACK, tags, neighbors));
    }

    @Property
    void orderOfNeighborsDoesNotMatter(@ForAll long seed, @ForAll("neighbors") List<CountryId> neighbors) {
        List<CountryId> shuffled = new ArrayList<>(neighbors);
        Collections.reverse(shuffled);

        assertThat(BackstoryWheel.generate(Rng.of(seed), PACK, Set.of(), shuffled))
                .isEqualTo(BackstoryWheel.generate(Rng.of(seed), PACK, Set.of(), neighbors));
    }

    @Property
    void backstoryRespectsBalanceChronologyAndConditions(
            @ForAll long seed, @ForAll("tags") Set<String> tags, @ForAll("neighbors") List<CountryId> neighbors) {
        Backstory backstory = BackstoryWheel.generate(Rng.of(seed), PACK, tags, neighbors);

        // Тестовий контент має досить фрагментів без умов, тож кількість завжди повна.
        assertThat(COUNT.contains(backstory.entries().size())).isTrue();
        assertThat(backstory.rolls()).hasSize(backstory.entries().size() + 1);

        Set<String> current = new TreeSet<>(tags);
        int previous = BackstoryFragmentDef.EARLIEST_YEAR;
        for (BackstoryEntry entry : backstory.entries()) {
            BackstoryFragmentDef fragment = entry.fragment();
            assertThat(fragment.available(current, !neighbors.isEmpty())).isTrue();
            assertThat(fragment.weightFor(current)).isPositive();
            assertThat(entry.year()).isGreaterThanOrEqualTo(previous);
            current.addAll(fragment.adds());
            previous = entry.year();
        }
        assertThat(backstory.tags()).containsExactlyElementsOf(current);
        backstory.neighbor().ifPresent(neighbor -> assertThat(neighbors).contains(neighbor));
    }

    @Provide
    Arbitrary<Set<String>> tags() {
        return Arbitraries.of("democratic", "junta", "pacifist", "civil_war").set();
    }

    @Provide
    Arbitrary<List<CountryId>> neighbors() {
        return Arbitraries.integers()
                .between(1, 40)
                .map(CountryId::of)
                .list()
                .uniqueElements()
                .ofMaxSize(5);
    }
}
