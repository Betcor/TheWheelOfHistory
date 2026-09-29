package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Властивості колеса назви на довільних seed, підкласифікаціях і зайнятих назвах. */
class NameWheelPropertiesTest {

    @Property
    void sameSeedGivesSameName(@ForAll long seed, @ForAll("subs") SubIdeologyId sub) {
        assertThat(NameWheel.generate(Rng.of(seed), TestNames.PACK, sub, Set.of()))
                .isEqualTo(NameWheel.generate(Rng.of(seed), TestNames.PACK, sub, Set.of()));
    }

    @Property
    void candidatesAreFreeAndDistinct(
            @ForAll long seed, @ForAll("subs") SubIdeologyId sub, @ForAll("taken") Set<String> taken) {
        StartName name = NameWheel.generate(Rng.of(seed), TestNames.PACK, sub, taken);

        assertThat(name.candidates())
                .hasSize(TestNames.PACK.balance().generation().nameCandidates());
        TreeSet<String> names = new TreeSet<>(taken);
        for (NameCandidate candidate : name.candidates()) {
            assertThat(names.add(candidate.name().fullName().nominative())).isTrue();
        }
    }

    @Property
    void takenNamesDoNotChangeChosenSector(@ForAll long seed, @ForAll("taken") Set<String> taken) {
        // Обертання — окремий потік: зайняті назви змінюють кандидатів, але не номер сектора.
        assertThat(NameWheel.generate(Rng.of(seed), TestNames.PACK, NameWheelTest.DIRECT, taken)
                        .chosen())
                .isEqualTo(NameWheel.generate(Rng.of(seed), TestNames.PACK, NameWheelTest.DIRECT, Set.of())
                        .chosen());
    }

    @Provide
    Arbitrary<SubIdeologyId> subs() {
        return Arbitraries.of(NameWheelTest.LIBERAL, NameWheelTest.DIRECT, NameWheelTest.ABSOLUTE);
    }

    /** Кілька назв з тих, що дає тестовий контент. */
    @Provide
    Arbitrary<Set<String>> taken() {
        return Arbitraries.of(
                        "Республіка Салан",
                        "Республіка Маран",
                        "Об'єднані Провінції Велор",
                        "Королівство Салан",
                        "Королівство Торенія",
                        "Республіка Гарор")
                .set();
    }
}
