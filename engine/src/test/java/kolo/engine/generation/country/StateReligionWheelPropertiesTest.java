package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.StateReligionDef;
import kolo.engine.content.TagCondition;
import kolo.engine.content.TestReligions;
import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Властивості колеса релігії держави на довільних seed, світах і мітках ладу. */
class StateReligionWheelPropertiesTest {

    @Property
    void sameSeedGivesSameReligion(@ForAll long seed, @ForAll @IntRange(min = 1, max = 40) int countries) {
        List<StartReligion> religions = world(seed, countries);

        assertThat(generate(seed, religions)).isEqualTo(generate(seed, religions));
    }

    @Property
    void resultIsAWorldReligionOrSecular(@ForAll long seed, @ForAll @IntRange(min = 1, max = 40) int countries) {
        List<StartReligion> religions = world(seed, countries);
        StartStateReligion result = generate(seed, religions);

        if (result.secular()) {
            assertThat(result.tags()).containsExactly("secular");
            assertThat(result.modifiers()).isEmpty();
        } else {
            assertThat(result.religion().getAsInt()).isBetween(0, religions.size() - 1);
            assertThat(result.tags())
                    .isEqualTo(religions.get(result.religion().getAsInt()).tags());
        }
        assertThat(result.rolls()).allMatch(roll -> roll.advantage() == 0);
    }

    @Property
    void weightsSumToTenThousandAndSecularIsExcludedWhenForbidden(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 40) int countries,
            @ForAll boolean theocratic,
            @ForAll boolean socialist) {
        StateReligionDef def = new StateReligionDef(
                100,
                TestReligions.secular(
                        50, Map.of("socialist", 150), new TagCondition(List.of(), List.of(), List.of("theocratic"))));
        Set<String> tags = new TreeSet<>();
        if (theocratic) {
            tags.add("theocratic");
        }
        if (socialist) {
            tags.add("socialist");
        }
        List<Sector<OptionalInt>> sectors = StateReligionWheel.sectors(def, tags, world(seed, countries));

        assertThat(sectors.stream().anyMatch(sector -> sector.value().isEmpty()))
                .isEqualTo(!theocratic);
        assertThat(Wheel.applyAdvantage(sectors, 0, 50).stream()
                        .mapToInt(Sector::weightBp)
                        .sum())
                .isEqualTo(10_000);
    }

    private static List<StartReligion> world(long seed, int countries) {
        StartReligions world = WorldReligionsWheel.generate(Rng.of(seed).fork("world"), TestNames.PACK, countries);
        return world.religions();
    }

    private static StartStateReligion generate(long seed, List<StartReligion> religions) {
        return StateReligionWheel.generate(Rng.of(seed), TestNames.PACK, Set.of(), religions);
    }
}
