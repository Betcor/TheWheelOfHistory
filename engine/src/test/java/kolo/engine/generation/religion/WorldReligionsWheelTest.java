package kolo.engine.generation.religion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.CountRange;
import kolo.engine.content.TestReligions;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldReligionsWheelTest {

    @Test
    void sameSeedGivesSameWorld() {
        assertThat(generate(42, 10)).isEqualTo(generate(42, 10));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 9, 20, 40})
    void countFollowsTableRowForCountries(int countries) {
        CountRange expected = countries <= TestReligions.FEW_COUNTRIES.maxCountries()
                ? TestReligions.FEW_COUNTRIES.religions()
                : TestReligions.MANY_COUNTRIES.religions();
        TreeSet<Integer> seen = new TreeSet<>();
        for (long seed = 0; seed < 100; seed++) {
            StartReligions world = generate(seed, countries);

            assertThat(world.countRoll().kind()).isEqualTo(WorldReligionsWheel.COUNT_KIND);
            assertThat(world.countRoll().resultSectorId())
                    .isEqualTo("religions_" + world.religions().size());
            assertThat(world.countRoll().sectors())
                    .extracting(RolledSector::id)
                    .containsExactlyElementsOf(ids(expected));
            seen.add(world.religions().size());
        }
        assertThat(seen).containsExactlyElementsOf(counts(expected));
    }

    @Test
    void countWheelHasNoAdvantage() {
        RollRecord roll = generate(3, 10).countRoll();

        assertThat(roll.advantage()).isZero();
        assertThat(roll.sectors()).allMatch(sector -> sector.tier() == OutcomeTier.PARTIAL);
        assertThat(roll.sectors()).allMatch(sector -> sector.quality() == ReligionWheel.QUALITY);
    }

    @Test
    void namesInWorldAreDistinct() {
        for (long seed = 0; seed < 200; seed++) {
            TreeSet<String> names = new TreeSet<>();
            for (StartReligion religion : generate(seed, 20).religions()) {
                assertThat(names.add(religion.name().nominative())).isTrue();
            }
        }
    }

    @Test
    void religionsUseOwnStreams() {
        // Кожна релігія — зі свого потоку: однакова кількість дає ті самі релігії (з поправкою на зайняті назви).
        StartReligions world = generate(7, 20);
        for (int i = 0; i < world.religions().size(); i++) {
            StartReligion alone = ReligionWheel.generate(Rng.of(7).fork("religion:" + i), TestNames.PACK, Set.of());
            assertThat(world.religions().get(i).archetype()).isEqualTo(alone.archetype());
            assertThat(world.religions().get(i).rolls()).isEqualTo(alone.rolls());
        }
    }

    @Test
    void rollsGoInOrderOfThrows() {
        StartReligions world = generate(11, 20);

        List<RollRecord> expected = new ArrayList<>();
        expected.add(world.countRoll());
        world.religions().forEach(religion -> expected.addAll(religion.rolls()));
        assertThat(world.rolls()).containsExactlyElementsOf(expected);
    }

    @Test
    void worldNeedsCountries() {
        assertThatThrownBy(() -> generate(1, 0))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    private static StartReligions generate(long seed, int countries) {
        return WorldReligionsWheel.generate(Rng.of(seed), TestNames.PACK, countries);
    }

    private static List<String> ids(CountRange range) {
        return counts(range).stream().map(count -> "religions_" + count).toList();
    }

    private static List<Integer> counts(CountRange range) {
        List<Integer> counts = new ArrayList<>();
        for (int count = range.min(); count <= range.max(); count++) {
            counts.add(count);
        }
        return counts;
    }
}
