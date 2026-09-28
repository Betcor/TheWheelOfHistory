package kolo.engine.wheel;

import static kolo.engine.wheel.Wheels.CONSTRUCTION;
import static kolo.engine.wheel.Wheels.CONSTRUCTION_KIND;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.rng.Rng;
import kolo.engine.state.Season;
import org.junit.jupiter.api.Test;

/**
 * Інтеграція колеса з потоками RNG року: seed світу → seed року → {@code fork} підсистеми → обертання.
 *
 * <p>Саме на цій зв'язці тримаються реплеї й попередній перегляд шансів на клієнті.
 */
class WheelRngIntegrationTest {

    private static final long WORLD_SEED = 1_970_0101L;
    private static final WheelKind BATTLE = new WheelKind("battle");

    @Test
    void extraSpinsInOneSubsystemDoNotShiftAnother() {
        List<RollRecord> baseline = simulateYear(7, 0);
        List<RollRecord> withExtraConstruction = simulateYear(7, 5);

        List<RollRecord> baselineWar = war(baseline);
        assertThat(war(withExtraConstruction)).isEqualTo(baselineWar);
        assertThat(construction(withExtraConstruction))
                .hasSize(construction(baseline).size() + 5);
    }

    @Test
    void yearIsReproducible() {
        assertThat(simulateYear(30, 2)).isEqualTo(simulateYear(30, 2));
    }

    @Test
    void recordedWeightsMatchClientPreview() {
        // Клієнт рахує попередній перегляд тим самим applyAdvantage, без RNG.
        Advantage advantage = Advantage.of(List.of(new AppliedModifier("army_training", "modifier.training", 35)));
        List<Sector<String>> preview = Wheel.applyAdvantage(CONSTRUCTION, advantage.value(), 70);

        RollRecord record = Wheel.spin(
                        Rng.of(Rng.mix(WORLD_SEED, 3)).fork("construction:cty_1"),
                        CONSTRUCTION_KIND,
                        CONSTRUCTION,
                        advantage,
                        70,
                        3,
                        null)
                .record();

        for (int i = 0; i < preview.size(); i++) {
            assertThat(record.sectors().get(i).id()).isEqualTo(preview.get(i).id());
            assertThat(record.sectors().get(i).weightBp())
                    .isEqualTo(preview.get(i).weightBp());
        }
    }

    /** Будівництво для двох держав, потім бої по сезонах на одному фронті. */
    private static List<RollRecord> simulateYear(int turn, int extraConstructionSpins) {
        Rng year = Rng.of(Rng.mix(WORLD_SEED, turn));
        List<RollRecord> records = new ArrayList<>();

        for (String country : new String[] {"cty_1", "cty_2"}) {
            Rng construction = year.fork("construction:" + country);
            int spins = 3 + (country.equals("cty_1") ? extraConstructionSpins : 0);
            for (int i = 0; i < spins; i++) {
                records.add(Wheel.spin(construction, CONSTRUCTION_KIND, CONSTRUCTION, Advantage.NONE, 100, turn, null)
                        .record());
            }
        }
        for (Season season : Season.values()) {
            Rng front = year.fork("war:frn_1:" + season);
            Advantage winter = season == Season.WINTER
                    ? Advantage.of(List.of(new AppliedModifier("winter", "modifier.season.winter", -5)))
                    : Advantage.NONE;
            records.add(Wheel.spin(front, BATTLE, CONSTRUCTION, winter, 100, turn, season)
                    .record());
        }
        return records;
    }

    private static List<RollRecord> war(List<RollRecord> records) {
        return records.stream().filter(r -> r.kind().equals(BATTLE)).toList();
    }

    private static List<RollRecord> construction(List<RollRecord> records) {
        return records.stream().filter(r -> r.kind().equals(CONSTRUCTION_KIND)).toList();
    }
}
