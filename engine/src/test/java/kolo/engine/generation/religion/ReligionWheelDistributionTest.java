package kolo.engine.generation.religion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import kolo.engine.content.ArchetypeId;
import kolo.engine.content.AspectId;
import kolo.engine.content.FaithFormId;
import kolo.engine.content.TestReligions;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.Sex;
import org.junit.jupiter.api.Test;

/**
 * Розподіл частин релігії відповідає вагам шаблону, кількості, форми назви й стать постаті — рівноймовірні. Seed-и
 * фіксовані, тож тест детермінований; пороги — χ² для p = 0,001.
 */
class ReligionWheelDistributionTest {

    private static final int RUNS = 20_000;
    private static final double CHI_SQUARED_1_DOF = 10.83;
    private static final double CHI_SQUARED_2_DOF = 13.82;

    private static final ArchetypeId POLYTHEISM = new ArchetypeId("polytheism");

    @Test
    void archetypesFollowWeights() {
        long[] counts = new long[2];
        for (long seed = 0; seed < RUNS; seed++) {
            counts[generate(seed).archetype().equals(POLYTHEISM) ? 1 : 0]++;
        }
        assertThat(chiSquared(counts, new long[] {100, 100})).isLessThan(CHI_SQUARED_1_DOF);
    }

    @Test
    void firstAspectOfPolytheismFollowsTaggedWeights() {
        // Політеїзм: війна 100 + 50, знання 100, море 100.
        long[] counts = new long[3];
        for (long seed = 0; seed < RUNS; seed++) {
            StartReligion religion = generate(seed);
            if (religion.archetype().equals(POLYTHEISM)) {
                AspectId first = religion.aspects().getFirst();
                counts[
                        switch (first.value()) {
                            case "war" -> 0;
                            case "knowledge" -> 1;
                            default -> 2;
                        }]++;
            }
        }
        assertThat(chiSquared(counts, new long[] {150, 100, 100})).isLessThan(CHI_SQUARED_2_DOF);
    }

    @Test
    void aspectAndDogmaCountsAreUniform() {
        long[] aspects = new long[2];
        long[] dogmas = new long[2];
        for (long seed = 0; seed < RUNS; seed++) {
            StartReligion religion = generate(seed);
            aspects[religion.aspects().size() - TestReligions.BALANCE.aspects().min()]++;
            dogmas[religion.dogmas().size() - TestReligions.BALANCE.dogmas().min()]++;
        }
        assertThat(chiSquared(aspects, new long[] {1, 1})).isLessThan(CHI_SQUARED_1_DOF);
        assertThat(chiSquared(dogmas, new long[] {1, 1})).isLessThan(CHI_SQUARED_1_DOF);
    }

    @Test
    void polytheismFigureSexAndFormAreHalfAndHalf() {
        long[] sexes = new long[Sex.values().length];
        long[] forms = new long[2];
        for (long seed = 0; seed < RUNS; seed++) {
            StartReligion religion = generate(seed);
            if (religion.archetype().equals(POLYTHEISM)) {
                sexes[religion.figureSex().ordinal()]++;
                forms[religion.faithForm().equals(new FaithFormId("temple")) ? 1 : 0]++;
            }
        }
        assertThat(chiSquared(sexes, new long[] {1, 1})).isLessThan(CHI_SQUARED_1_DOF);
        assertThat(chiSquared(forms, new long[] {1, 1})).isLessThan(CHI_SQUARED_1_DOF);
    }

    @Test
    void religionCountIsUniformInTableRow() {
        long[] counts = new long[2];
        int min = TestReligions.FEW_COUNTRIES.religions().min();
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    WorldReligionsWheel.generate(Rng.of(seed), TestNames.PACK, 5)
                                    .religions()
                                    .size()
                            - min]++;
        }
        assertThat(chiSquared(counts, new long[] {1, 1})).isLessThan(CHI_SQUARED_1_DOF);
    }

    private static StartReligion generate(long seed) {
        return ReligionWheel.generate(Rng.of(seed), TestNames.PACK, Set.of());
    }

    /** χ² спостережених частот проти очікуваних за відносними вагами. */
    private static double chiSquared(long[] counts, long[] weights) {
        long total = 0;
        long weightTotal = 0;
        for (int i = 0; i < counts.length; i++) {
            total += counts[i];
            weightTotal += weights[i];
        }
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = (double) total * weights[i] / weightTotal;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
