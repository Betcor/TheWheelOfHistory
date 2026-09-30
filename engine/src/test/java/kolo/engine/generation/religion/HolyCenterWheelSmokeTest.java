package kolo.engine.generation.religion;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: кожна релігія світу отримує святий центр на суходолі. */
class HolyCenterWheelSmokeTest {

    @Test
    void everyReligionGetsHolyCenter() {
        StartReligions religions =
                WorldReligionsWheel.generate(Rng.of(1970), TestHolyCenters.PACK, TestHolyCenters.MAP.countries());

        StartHolyCenters centers = HolyCenterWheel.generate(
                Rng.of(1971),
                TestHolyCenters.PACK,
                TestHolyCenters.MAP,
                religions.religions().size(),
                TestHolyCenters.roundRobin(religions.religions().size()));

        assertThat(centers.centers()).hasSize(religions.religions().size());
        assertThat(centers.centers())
                .allSatisfy(center -> assertThat(TestHolyCenters.MAP.fertility().fertility(center.cell()))
                        .isPresent());
    }
}
