package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRulesDef;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: три дуже добрі результати поспіль запускають «Золоту добу», і держава отримує нагороду. */
class StreakWheelSmokeTest {

    @Test
    void goldenAgeStreakGivesReward() {
        StreakRulesDef rules = TestRegime.PACK.balance().streaks();
        Streaks streaks = Streaks.START;
        Streaks.Step step = null;
        for (int i = 0; i < rules.length(); i++) {
            step = streaks.next(rules, 100);
            streaks = step.streaks();
        }

        assertThat(step.triggered()).contains(StreakKind.GOLDEN_AGE);
        StreakBonus bonus = StreakWheel.generate(Rng.of(1970).fork("streak"), TestRegime.PACK, StreakKind.GOLDEN_AGE);
        assertThat(bonus.tags()).contains("world_attention");
        assertThat(bonus.roll().kind().id()).isEqualTo("generation_golden_age");
    }
}
