package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestPeople.PACK;
import static kolo.engine.generation.country.TestPeople.STYLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import kolo.engine.rng.Rng;
import kolo.engine.state.PersonKind;
import kolo.engine.state.Sex;
import kolo.engine.wheel.Sector;
import org.junit.jupiter.api.Test;

/**
 * Розподіл постатей відповідає вагам типів за мітками держави, стать — навпіл, кількості й вік — рівноймовірні.
 * Seed-и фіксовані, тож тест детермінований; пороги — χ² для p = 0,001.
 */
class PeopleWheelDistributionTest {

    private static final int RUNS = 30_000;
    private static final double CHI_SQUARED_1_DOF = 10.83;
    private static final double CHI_SQUARED_2_DOF = 13.82;
    private static final double CHI_SQUARED_8_DOF = 26.12;
    private static final double CHI_SQUARED_45_DOF = 80.08;

    @Test
    void kindsFollowWeightsWithoutTags() {
        assertKindsFollowWeights(Set.of());
    }

    @Test
    void juntaMakesGeneralsMoreFrequent() {
        long[] counts = assertKindsFollowWeights(Set.of("junta"));

        // Генерал 40 з 120 замість 10 з 90.
        assertThat(counts[PersonKind.GENERAL.ordinal()]).isGreaterThan(sum(counts) / 4);
    }

    @Test
    void sexIsHalfAndHalf() {
        long[] counts = new long[Sex.values().length];
        for (long seed = 0; seed < RUNS; seed++) {
            for (StartPerson person : generate(seed, Set.of()).people()) {
                counts[person.sex().ordinal()]++;
            }
        }
        assertThat(chiSquared(counts, new long[] {1, 1})).isLessThan(CHI_SQUARED_1_DOF);
    }

    @Test
    void peopleAndTraitCountsAreUniform() {
        long[] people = new long[TestPeople.PEOPLE.max() + 1];
        long[] traitCounts = new long[TestPeople.TRAIT_COUNT.max() + 1];
        for (long seed = 0; seed < RUNS; seed++) {
            StartPeople result = generate(seed, Set.of());
            people[result.people().size()]++;
            for (StartPerson person : result.people()) {
                traitCounts[person.rolls().get(1).resultSectorId().charAt("traits_".length()) - '0']++;
            }
        }
        assertThat(chiSquared(slice(people, 1), new long[] {1, 1, 1})).isLessThan(CHI_SQUARED_2_DOF);
        assertThat(chiSquared(slice(traitCounts, 1), new long[] {1, 1, 1})).isLessThan(CHI_SQUARED_2_DOF);
    }

    @Test
    void agesAreUniform() {
        int span = TestPeople.AGE.max() - TestPeople.AGE.min() + 1;
        long[] counts = new long[span];
        for (long seed = 0; seed < RUNS; seed++) {
            for (StartPerson person : generate(seed, Set.of()).people()) {
                counts[person.age() - TestPeople.AGE.min()]++;
            }
        }
        long[] equal = new long[span];
        Arrays.fill(equal, 1);
        assertThat(chiSquared(counts, equal)).isLessThan(CHI_SQUARED_45_DOF);
    }

    private static long[] assertKindsFollowWeights(Set<String> tags) {
        long[] counts = new long[PersonKind.values().length];
        for (long seed = 0; seed < RUNS; seed++) {
            for (StartPerson person : generate(seed, tags).people()) {
                counts[person.kind().ordinal()]++;
            }
        }
        List<Sector<PersonKind>> sectors = PeopleWheel.kindSectors(PACK, tags);
        long[] weights = new long[PersonKind.values().length];
        sectors.forEach(sector -> weights[sector.value().ordinal()] = sector.weightBp());
        assertThat(chiSquared(counts, weights)).isLessThan(CHI_SQUARED_8_DOF);
        return counts;
    }

    private static StartPeople generate(long seed, Set<String> tags) {
        return PeopleWheel.generate(Rng.of(seed), PACK, tags, STYLE, Set.of());
    }

    private static long[] slice(long[] counts, int from) {
        return Arrays.copyOfRange(counts, from, counts.length);
    }

    private static long sum(long[] values) {
        long total = 0;
        for (long value : values) {
            total += value;
        }
        return total;
    }

    /** χ² спостережених частот проти очікуваних за відносними вагами. */
    private static double chiSquared(long[] counts, long[] weights) {
        long total = sum(counts);
        long weightTotal = sum(weights);
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = (double) total * weights[i] / weightTotal;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
