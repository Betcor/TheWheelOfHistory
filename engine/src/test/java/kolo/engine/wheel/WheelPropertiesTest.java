package kolo.engine.wheel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.rng.Rng;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** Властивості колеса на довільних секторах, перевагах і силах переваги. */
class WheelPropertiesTest {

    @Property
    void weightsSumToTotal(
            @ForAll("wheels") List<Sector<String>> sectors,
            @ForAll @IntRange(min = -100, max = 100) int advantage,
            @ForAll @IntRange(max = 100) int strength) {
        int sum = 0;
        for (Sector<String> sector : Wheel.applyAdvantage(sectors, advantage, strength)) {
            assertThat(sector.weightBp()).isNotNegative();
            sum += sector.weightBp();
        }
        assertThat(sum).isEqualTo(Wheel.TOTAL_BP);
    }

    @Property
    void criticalSectorsKeepMinimum(
            @ForAll("wheels") List<Sector<String>> sectors,
            @ForAll @IntRange(min = -100, max = 100) int advantage,
            @ForAll @IntRange(max = 100) int strength) {
        for (Sector<String> sector : Wheel.applyAdvantage(sectors, advantage, strength)) {
            if (sector.tier().isCritical()) {
                assertThat(sector.weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
            }
        }
    }

    @Property
    void keepsSectorsAndOrder(
            @ForAll("wheels") List<Sector<String>> sectors,
            @ForAll @IntRange(min = -100, max = 100) int advantage,
            @ForAll @IntRange(max = 100) int strength) {
        List<Sector<String>> result = Wheel.applyAdvantage(sectors, advantage, strength);

        assertThat(result).hasSameSizeAs(sectors);
        for (int i = 0; i < sectors.size(); i++) {
            assertThat(result.get(i))
                    .isEqualTo(sectors.get(i).withWeight(result.get(i).weightBp()));
        }
    }

    @Property
    void normalizedWheelIsFixedPointOfZeroAdvantage(@ForAll("wheels") List<Sector<String>> sectors) {
        List<Sector<String>> normalized = Wheel.applyAdvantage(sectors, 0, 100);

        assertThat(Wheel.applyAdvantage(normalized, 0, 100)).isEqualTo(normalized);
    }

    @Property
    void sameSeedGivesSameSpin(
            @ForAll long seed,
            @ForAll("wheels") List<Sector<String>> sectors,
            @ForAll @IntRange(min = -100, max = 100) int advantage) {
        Advantage adv = new Advantage(advantage, List.of());

        RollRecord first = Wheel.spin(Rng.of(seed), Wheels.CONSTRUCTION_KIND, sectors, adv, 100, 5, null)
                .record();
        RollRecord second = Wheel.spin(Rng.of(seed), Wheels.CONSTRUCTION_KIND, sectors, adv, 100, 5, null)
                .record();

        assertThat(second).isEqualTo(first);
    }

    @Property
    void spinResultHasPositiveWeight(
            @ForAll long seed,
            @ForAll("wheels") List<Sector<String>> sectors,
            @ForAll @IntRange(min = -100, max = 100) int advantage) {
        WheelSpin<String> spin = Wheel.spin(
                Rng.of(seed), Wheels.CONSTRUCTION_KIND, sectors, new Advantage(advantage, List.of()), 100, 0, null);

        assertThat(spin.record().result().weightBp()).isPositive();
        assertThat(spin.outcome().weightBp()).isEqualTo(spin.record().result().weightBp());
    }

    @Provide
    Arbitrary<List<Sector<String>>> wheels() {
        Arbitrary<OutcomeTier> tiers = Arbitraries.of(OutcomeTier.class);
        Arbitrary<Integer> weights = Arbitraries.oneOf(
                Arbitraries.integers().between(0, 10), Arbitraries.integers().between(0, Wheel.TOTAL_BP));
        return Combinators.combine(weights, tiers)
                .as(WeightAndTier::new)
                .list()
                .ofMinSize(1)
                .ofMaxSize(12)
                .filter(list -> list.stream().mapToLong(WeightAndTier::weight).sum() > 0)
                .map(WheelPropertiesTest::toSectors);
    }

    private record WeightAndTier(int weight, OutcomeTier tier) {}

    private static List<Sector<String>> toSectors(List<WeightAndTier> specs) {
        List<Sector<String>> sectors = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            sectors.add(
                    Wheels.sector("s" + i, specs.get(i).weight(), specs.get(i).tier()));
        }
        return sectors;
    }
}
