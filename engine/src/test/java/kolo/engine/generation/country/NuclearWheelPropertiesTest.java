package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestNuclear.IRON;
import static kolo.engine.generation.country.TestNuclear.PACK;
import static kolo.engine.generation.country.TestNuclear.URANIUM;
import static kolo.engine.generation.country.TestNuclear.WARHEADS;
import static kolo.engine.generation.country.TestNuclear.development;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.ResourceId;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.state.Development;
import kolo.engine.state.NuclearStatus;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Властивості колеса ядерного статусу на довільних seed, перевагах, розвиненості й ресурсах. */
class NuclearWheelPropertiesTest {

    @Property
    void sameSeedGivesSameStatus(
            @ForAll long seed, @ForAll @IntRange(min = Development.MIN, max = Development.MAX) int energy) {
        List<Modifier> modifiers = TestNuclear.REGIME.modifiers();
        List<ResourceId> resources = List.of(URANIUM);

        assertThat(NuclearWheel.generate(Rng.of(seed), PACK, modifiers, development(energy), resources))
                .isEqualTo(NuclearWheel.generate(Rng.of(seed), PACK, modifiers, development(energy), resources));
    }

    @Property
    void statusIsConsistentWithRulesAndRolls(
            @ForAll long seed,
            @ForAll @IntRange(min = -150, max = 150) int regime,
            @ForAll @IntRange(min = Development.MIN, max = Development.MAX) int energy,
            @ForAll boolean uranium) {
        // Два внески: сума може виходити за межі переваги, колесо обрізає її.
        List<Modifier> modifiers =
                List.of(TestNuclear.modifier("a", regime / 2), TestNuclear.modifier("b", regime - regime / 2));
        List<ResourceId> resources = uranium ? List.of(URANIUM, IRON) : List.of(IRON);

        StartNuclear nuclear = NuclearWheel.generate(Rng.of(seed), PACK, modifiers, development(energy), resources);

        RollRecord roll = nuclear.rolls().getFirst();
        boolean allowed = uranium && energy >= NuclearWheel.ARSENAL_MIN_ENERGY_LEVEL;
        assertThat(roll.advantage()).isEqualTo(Math.clamp(regime + energy * TestNuclear.ENERGY_ADVANTAGE, -100, 100));
        assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                .isEqualTo(Wheel.TOTAL_BP);
        assertThat(roll.sectors()).hasSize(allowed ? 3 : 2);
        if (allowed) {
            assertThat(roll.sectors().getLast().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        }
        assertThat(roll.resultSectorId()).isEqualTo(nuclear.status().key());
        if (nuclear.status() == NuclearStatus.ARSENAL) {
            assertThat(allowed).isTrue();
            assertThat(WARHEADS.contains(nuclear.warheads())).isTrue();
            assertThat(nuclear.rolls()).hasSize(2);
        } else {
            assertThat(nuclear.warheads()).isZero();
            assertThat(nuclear.rolls()).hasSize(1);
        }
    }
}
