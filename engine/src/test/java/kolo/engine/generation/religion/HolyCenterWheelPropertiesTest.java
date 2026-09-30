package kolo.engine.generation.religion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.rng.Rng;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** Властивості колеса святого центру на довільних seed і довільному розподілі релігій між державами. */
class HolyCenterWheelPropertiesTest {

    private static final WorldMap MAP = TestHolyCenters.MAP;
    private static final int RELIGIONS = 4;

    @Property
    void sameSeedGivesSameCenters(@ForAll long seed, @ForAll("countryReligions") List<OptionalInt> religions) {
        assertThat(HolyCenterWheel.generate(Rng.of(seed), TestHolyCenters.PACK, MAP, RELIGIONS, religions))
                .isEqualTo(HolyCenterWheel.generate(Rng.of(seed), TestHolyCenters.PACK, MAP, RELIGIONS, religions));
    }

    @Property
    void centerIsLandOfFollowersOrUnclaimed(
            @ForAll long seed, @ForAll("countryReligions") List<OptionalInt> religions) {
        StartHolyCenters centers =
                HolyCenterWheel.generate(Rng.of(seed), TestHolyCenters.PACK, MAP, RELIGIONS, religions);

        assertThat(centers.centers()).hasSize(RELIGIONS);
        for (int r = 0; r < RELIGIONS; r++) {
            int cell = centers.cell(r);
            assertThat(MAP.fertility().fertility(cell)).isPresent();
            int owner = MAP.placement().country(cell);
            if (MAP.placement().isClaimed(cell)) {
                assertThat(religions.get(owner)).isEqualTo(OptionalInt.of(r));
            }
            assertThat(centers.rolls().get(r).sectors()).allMatch(sector -> sector.weightBp() >= 0);
        }
    }

    @Property
    void religionCountDoesNotShiftEarlierCenters(
            @ForAll long seed, @ForAll @IntRange(min = 1, max = RELIGIONS) int fewer) {
        List<OptionalInt> religions = TestHolyCenters.roundRobin(fewer);
        StartHolyCenters few = HolyCenterWheel.generate(Rng.of(seed), TestHolyCenters.PACK, MAP, fewer, religions);
        StartHolyCenters many = HolyCenterWheel.generate(Rng.of(seed), TestHolyCenters.PACK, MAP, RELIGIONS, religions);

        assertThat(many.centers().subList(0, fewer)).isEqualTo(few.centers());
    }

    @Provide
    Arbitrary<List<OptionalInt>> countryReligions() {
        Arbitrary<OptionalInt> religion = Arbitraries.integers()
                .between(-1, RELIGIONS - 1)
                .map(index -> index < 0 ? OptionalInt.empty() : OptionalInt.of(index));
        return religion.list().ofSize(MAP.countries()).map(ArrayList::new);
    }
}
