package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** Вага фрагмента на довільних добавках і мітках держави. */
class BackstoryWeightPropertiesTest {

    private static final List<String> TAGS = List.of("a", "b", "c", "d", "e", "f");

    @Property
    void weightStaysWithinRangeAndEqualsClampedSumOfPresentBonuses(
            @ForAll @IntRange(min = 1, max = BackstoryFragmentDef.MAX_WEIGHT) int base,
            @ForAll("bonuses") Map<String, Integer> bonuses,
            @ForAll("tagSets") Set<String> tags) {
        BackstoryFragmentDef fragment = new BackstoryFragmentDef(
                new BackstoryFragmentId("x"),
                base,
                50,
                1950,
                1960,
                TagCondition.NONE,
                new TreeMap<>(bonuses),
                false,
                List.of(),
                0,
                List.of(),
                new BackstoryText("text", "Подія {year} року."));

        long expected = base;
        for (String tag : tags) {
            expected += bonuses.getOrDefault(tag, 0);
        }
        int weight = fragment.weightFor(tags);

        assertThat(weight).isBetween(0, BackstoryFragmentDef.MAX_WEIGHT);
        assertThat(weight).isEqualTo(Math.clamp(expected, 0, BackstoryFragmentDef.MAX_WEIGHT));
    }

    @Provide
    Arbitrary<Map<String, Integer>> bonuses() {
        return Arbitraries.maps(
                        Arbitraries.of(TAGS),
                        Arbitraries.integers()
                                .between(-BackstoryFragmentDef.MAX_WEIGHT, BackstoryFragmentDef.MAX_WEIGHT))
                .ofMaxSize(TAGS.size());
    }

    @Provide
    Arbitrary<Set<String>> tagSets() {
        return Arbitraries.of(TAGS).set().ofMaxSize(TAGS.size()).map(TreeSet::new);
    }
}
