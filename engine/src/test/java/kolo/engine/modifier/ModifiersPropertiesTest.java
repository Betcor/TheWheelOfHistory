package kolo.engine.modifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kolo.engine.state.CountryStats;
import kolo.engine.state.Stat;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.WheelKind;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** Властивості обчислення ефективних значень на довільних базах і модифікаторах. */
class ModifiersPropertiesTest {

    private static final List<WheelKind> KINDS = List.of(TestModifiers.CONSTRUCTION, TestModifiers.BATTLE);

    @Property
    void effectiveStatsStayWithinRanges(
            @ForAll("stats") CountryStats base,
            @ForAll("modifiers") List<Modifier> modifiers,
            @ForAll @IntRange(max = 30) int turn) {
        CountryStats effective = Modifiers.effective(base, modifiers, turn);

        for (Stat stat : Stat.values()) {
            assertThat(stat.of(effective)).isBetween(stat.min(), stat.max());
        }
    }

    @Property
    void effectiveIsClampedBasePlusActiveSum(
            @ForAll("stats") CountryStats base,
            @ForAll("modifiers") List<Modifier> modifiers,
            @ForAll @IntRange(max = 30) int turn) {
        CountryStats effective = Modifiers.effective(base, modifiers, turn);

        for (Stat stat : Stat.values()) {
            long sum = stat.of(base);
            for (Modifier modifier : modifiers) {
                if (modifier.target().equals(ModifierTarget.stat(stat)) && modifier.isActiveAt(turn)) {
                    sum += modifier.value();
                }
            }
            assertThat(stat.of(effective)).isEqualTo(stat.clamp(sum));
        }
    }

    @Property
    void orderOfModifiersDoesNotChangeValues(
            @ForAll("stats") CountryStats base,
            @ForAll("modifiers") List<Modifier> modifiers,
            @ForAll @IntRange(max = 30) int turn) {
        List<Modifier> reversed = new ArrayList<>(modifiers);
        Collections.reverse(reversed);

        assertThat(Modifiers.effective(base, reversed, turn)).isEqualTo(Modifiers.effective(base, modifiers, turn));
        for (WheelKind kind : KINDS) {
            assertThat(Modifiers.advantage(reversed, kind, turn).value())
                    .isEqualTo(Modifiers.advantage(modifiers, kind, turn).value());
        }
    }

    @Property
    void advantageStaysWithinBoundsAndExplainsEveryActiveModifier(
            @ForAll("modifiers") List<Modifier> modifiers, @ForAll @IntRange(max = 30) int turn) {
        for (WheelKind kind : KINDS) {
            Advantage advantage = Modifiers.advantage(modifiers, kind, turn);

            assertThat(advantage.value()).isBetween(Advantage.MIN, Advantage.MAX);
            long expected = modifiers.stream()
                    .filter(m -> m.target().equals(ModifierTarget.wheel(kind)) && m.isActiveAt(turn))
                    .count();
            assertThat(advantage.modifiers()).hasSize((int) expected);
            long sum = advantage.modifiers().stream()
                    .mapToLong(AppliedModifier::value)
                    .sum();
            assertThat(advantage.value()).isEqualTo(Math.clamp(sum, Advantage.MIN, Advantage.MAX));
        }
    }

    @Property
    void pruningAtEndOfTurnDoesNotChangeNextTurn(
            @ForAll("stats") CountryStats base,
            @ForAll("modifiers") List<Modifier> modifiers,
            @ForAll @IntRange(max = 30) int turn) {
        List<Modifier> pruned = Modifiers.withoutExpired(modifiers, turn);

        assertThat(Modifiers.effective(base, pruned, turn + 1))
                .isEqualTo(Modifiers.effective(base, modifiers, turn + 1));
        for (WheelKind kind : KINDS) {
            assertThat(Modifiers.advantage(pruned, kind, turn + 1))
                    .isEqualTo(Modifiers.advantage(modifiers, kind, turn + 1));
        }
        assertThat(pruned).allMatch(m -> m.isActiveAt(turn + 1));
    }

    @Provide
    Arbitrary<CountryStats> stats() {
        Arbitrary<Integer> percent = Arbitraries.integers().between(0, 100);
        return Combinators.combine(
                        Arbitraries.longs().between(0, 1_000_000_000_000L),
                        percent,
                        percent,
                        percent,
                        percent,
                        percent,
                        Arbitraries.integers().between(0, 100_000))
                .as(CountryStats::new);
    }

    @Provide
    Arbitrary<List<Modifier>> modifiers() {
        Arbitrary<ModifierTarget> targets = Arbitraries.oneOf(
                Arbitraries.of(Stat.class).map(ModifierTarget::stat),
                Arbitraries.of(KINDS).map(ModifierTarget::wheel));
        Arbitrary<Integer> values = Arbitraries.oneOf(
                Arbitraries.integers().between(-100, 100),
                Arbitraries.integers().between(-1_000_000, 1_000_000));
        Arbitrary<Integer> expires = Arbitraries.integers().between(0, 30).injectNull(0.4);
        return Combinators.combine(targets, values, expires)
                .as(Spec::new)
                .list()
                .ofMaxSize(20)
                .map(ModifiersPropertiesTest::toModifiers);
    }

    private record Spec(ModifierTarget target, int value, Integer expiresAtTurn) {}

    private static List<Modifier> toModifiers(List<Spec> specs) {
        List<Modifier> modifiers = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            modifiers.add(new Modifier(
                    "mod_" + i,
                    new ModifierSource(SourceKind.EVENT, "evt_" + i),
                    spec.target(),
                    spec.value(),
                    spec.expiresAtTurn(),
                    "modifier.test"));
        }
        return modifiers;
    }
}
