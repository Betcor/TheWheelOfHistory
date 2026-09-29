package kolo.engine.generation.religion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DogmaId;
import kolo.engine.content.ReligionContent;
import kolo.engine.content.TestReligions;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** Властивості генерації релігій на довільних seed, зайнятих назвах і кількості держав. */
class ReligionWheelPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final ReligionContent RELIGIONS = PACK.religions();

    @Property
    void sameSeedGivesSameReligion(@ForAll long seed, @ForAll("taken") Set<String> taken) {
        assertThat(ReligionWheel.generate(Rng.of(seed), PACK, taken))
                .isEqualTo(ReligionWheel.generate(Rng.of(seed), PACK, taken));
    }

    @Property
    void religionRespectsTemplateAndBalance(@ForAll long seed, @ForAll("taken") Set<String> taken) {
        StartReligion religion = ReligionWheel.generate(Rng.of(seed), PACK, taken);

        assertThat(TestReligions.BALANCE.aspects().contains(religion.aspects().size()))
                .isTrue();
        assertThat(TestReligions.BALANCE.dogmas().contains(religion.dogmas().size()))
                .isTrue();
        List<DogmaId> seen = new ArrayList<>();
        for (DogmaId dogma : religion.dogmas()) {
            assertThat(seen).allMatch(other -> RELIGIONS.compatible(dogma, other));
            seen.add(dogma);
        }
        assertThat(RELIGIONS.archetype(religion.archetype()).orElseThrow().figureSexes())
                .contains(religion.figureSex());
        assertThat(RELIGIONS.faithFormsFor(religion.archetype()))
                .anyMatch(form -> form.id().equals(religion.faithForm()));
        assertThat(taken).doesNotContain(religion.name().nominative());
        assertThat(religion.rolls()).allMatch(roll -> roll.advantage() == 0);
    }

    @Property
    void worldReligionsHaveDistinctNamesAndCountFromTable(
            @ForAll long seed, @ForAll @IntRange(min = 1, max = 40) int countries) {
        StartReligions world = WorldReligionsWheel.generate(Rng.of(seed), PACK, countries);

        assertThat(TestReligions.BALANCE
                        .religions(countries)
                        .contains(world.religions().size()))
                .isTrue();
        TreeSet<String> names = new TreeSet<>();
        world.religions()
                .forEach(religion ->
                        assertThat(names.add(religion.name().nominative())).isTrue());
    }

    /** Кілька назв з тих, що дає тестовий контент. */
    @Provide
    Arbitrary<Set<String>> taken() {
        return Arbitraries.of("Шлях Велора", "Шлях Салана", "Храм Салана", "Храм Саліни", "Шлях Торарора")
                .set();
    }
}
