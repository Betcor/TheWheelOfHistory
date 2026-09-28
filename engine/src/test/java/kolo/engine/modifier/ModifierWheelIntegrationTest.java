package kolo.engine.modifier;

import static kolo.engine.modifier.TestModifiers.CONSTRUCTION;
import static kolo.engine.modifier.TestModifiers.onStat;
import static kolo.engine.modifier.TestModifiers.onWheel;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryStats;
import kolo.engine.state.Stat;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;

/**
 * Модифікатори → перевага → колесо → запис обертання. Запис має пояснювати шанси саме тими модифікаторами, що
 * діяли в цьому ході, разом із внесками, які система додає сама (тут — від стабільності).
 */
class ModifierWheelIntegrationTest {

    private static final List<Sector<String>> WHEEL = List.of(
            sector("crit_fail", 300, OutcomeTier.CRIT_FAIL),
            sector("fail", 1200, OutcomeTier.FAIL),
            sector("partial", 2500, OutcomeTier.PARTIAL),
            sector("success", 5000, OutcomeTier.SUCCESS),
            sector("crit_success", 1000, OutcomeTier.CRIT_SUCCESS));

    private static final CountryStats BASE = new CountryStats(10_000, 50, 50, 50, 50, 0, 10);

    private static final List<Modifier> MODIFIERS = List.of(
            onWheel("engineers", CONSTRUCTION, 20, null),
            onWheel("boom", CONSTRUCTION, 10, 3),
            onStat("reform", Stat.STABILITY, 20, null));

    @Test
    void rollRecordExplainsActiveModifiersAndSystemContributions() {
        RollRecord record = spin(3);

        assertThat(record.advantage()).isEqualTo(20 + 10 + 7);
        assertThat(record.modifiers())
                .containsExactly(
                        new AppliedModifier("engineers", "modifier.engineers", 20),
                        new AppliedModifier("boom", "modifier.boom", 10),
                        new AppliedModifier("stability", "modifier.stability", 7));
    }

    @Test
    void expiredModifierNoLongerShiftsOdds() {
        RollRecord active = spin(3);
        RollRecord expired = spin(4);

        assertThat(expired.advantage()).isEqualTo(20 + 7);
        assertThat(expired.modifiers()).extracting(AppliedModifier::sourceId).doesNotContain("boom");
        assertThat(expired.sectors().get(3).weightBp())
                .isLessThan(active.sectors().get(3).weightBp());
    }

    @Test
    void sameStateGivesSameRoll() {
        assertThat(spin(3)).isEqualTo(spin(3));
    }

    /** Будівництво: перевага від модифікаторів колеса плюс внесок ефективної стабільності. */
    private static RollRecord spin(int turn) {
        CountryStats effective = Modifiers.effective(BASE, MODIFIERS, turn);
        // Умовна формула для тесту: кожні 10 пунктів стабільності понад 0 — +1 перевага.
        int stabilityBonus = effective.stability() / 10;

        List<AppliedModifier> contributions =
                new ArrayList<>(Modifiers.contributions(MODIFIERS, ModifierTarget.wheel(CONSTRUCTION), turn));
        contributions.add(new AppliedModifier("stability", "modifier.stability", stabilityBonus));
        Advantage advantage = Advantage.of(contributions);

        Rng year = Rng.of(Rng.mix(1970L, turn));
        return Wheel.spin(year.fork("construction:cty_1"), CONSTRUCTION, WHEEL, advantage, 100, turn, null)
                .record();
    }

    private static Sector<String> sector(String id, int weightBp, OutcomeTier tier) {
        return new Sector<>(id, weightBp, id, tier.ordinal() * 25, tier, List.of());
    }
}
