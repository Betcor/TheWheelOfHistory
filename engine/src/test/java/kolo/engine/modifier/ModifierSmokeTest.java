package kolo.engine.modifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryStats;
import kolo.engine.state.Stat;
import kolo.engine.wheel.Advantage;
import org.junit.jupiter.api.Test;

/**
 * Смок-тест підсистеми модифікаторів: 100 років, щороку нові модифікатори з випадковими термінами, читання
 * ефективних статів і переваги, зняття прострочених у кінці року — без виходу за межі й без накопичення сміття.
 */
class ModifierSmokeTest {

    @Test
    void modifiersLiveThroughHundredYears() {
        CountryStats base = new CountryStats(50_000, 50, 50, 50, 50, 0, 100);
        List<Modifier> modifiers = new ArrayList<>();
        Stat[] stats = Stat.values();
        int issued = 0;

        for (int turn = 0; turn < 100; turn++) {
            Rng rng = Rng.of(Rng.mix(7L, turn)).fork("modifiers");
            for (int i = 0; i < 3; i++) {
                ModifierTarget target = rng.nextInt(2) == 0
                        ? ModifierTarget.stat(stats[rng.nextInt(stats.length)])
                        : ModifierTarget.wheel(TestModifiers.CONSTRUCTION);
                Integer expires = rng.nextInt(4) == 0 ? null : turn + rng.nextInt(5);
                modifiers.add(new Modifier(
                        "mod_" + issued++,
                        new ModifierSource(SourceKind.EVENT, "evt_" + turn),
                        target,
                        rng.nextInt(61) - 30,
                        expires,
                        "modifier.test"));
            }

            CountryStats effective = Modifiers.effective(base, modifiers, turn);
            for (Stat stat : stats) {
                assertThat(stat.of(effective)).isBetween(stat.min(), stat.max());
            }
            Advantage advantage = Modifiers.advantage(modifiers, TestModifiers.CONSTRUCTION, turn);
            assertThat(advantage.value()).isBetween(Advantage.MIN, Advantage.MAX);

            modifiers = new ArrayList<>(Modifiers.withoutExpired(modifiers, turn));
            int now = turn;
            assertThat(modifiers).allMatch(m -> m.isActiveAt(now + 1));
        }

        assertThat(issued).isEqualTo(300);
        // Строкові модифікатори живуть до 5 років, тож лишаються лише безстрокові й кілька останніх.
        assertThat(modifiers).hasSizeLessThan(issued / 2);
    }
}
