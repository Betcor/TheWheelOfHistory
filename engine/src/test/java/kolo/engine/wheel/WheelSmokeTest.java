package kolo.engine.wheel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/**
 * Смок-тест підсистеми колеса: основний сценарій від модифікаторів до запису обертання, крізь 50 років для
 * кількох держав, без жодного недійсного запису.
 */
class WheelSmokeTest {

    @Test
    void wheelsSpinThroughFiftyYears() {
        long worldSeed = 42;
        int records = 0;
        for (int turn = 0; turn < 50; turn++) {
            Rng year = Rng.of(Rng.mix(worldSeed, turn));
            for (int country = 1; country <= 10; country++) {
                Advantage advantage = Advantage.of(List.of(
                        new AppliedModifier("stability", "modifier.stability", country * 7 - 35),
                        new AppliedModifier("investment", "modifier.investment", turn % 20)));
                WheelSpin<String> spin = Wheel.spin(
                        year.fork("construction:cty_" + country),
                        Wheels.CONSTRUCTION_KIND,
                        Wheels.CONSTRUCTION,
                        advantage,
                        100,
                        turn,
                        null);

                RollRecord record = spin.record();
                assertThat(record.sectors().stream()
                                .mapToInt(RolledSector::weightBp)
                                .sum())
                        .isEqualTo(Wheel.TOTAL_BP);
                assertThat(record.turn()).isEqualTo(turn);
                assertThat(record.result().weightBp()).isPositive();
                records++;
            }
        }
        assertThat(records).isEqualTo(500);
    }
}
