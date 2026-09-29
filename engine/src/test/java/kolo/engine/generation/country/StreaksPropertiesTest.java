package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRulesDef;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** Властивості лічильника стріків на довільних послідовностях якостей. */
class StreaksPropertiesTest {

    private static final StreakRulesDef RULES = new StreakRulesDef(85, 15, 3);

    @Property
    void eachKindFiresAtMostOnce(@ForAll @Size(max = 40) List<@IntRange(min = 0, max = 100) Integer> qualities) {
        List<StreakKind> fired = fired(qualities);

        assertThat(fired).doesNotHaveDuplicates();
    }

    @Property
    void firesExactlyWhenLengthExtremesInARowAppearFirstTime(
            @ForAll @Size(max = 40) List<@IntRange(min = 0, max = 100) Integer> qualities) {
        // Незалежна модель: шукаємо перший відрізок із трьох дуже добрих поспіль, що не перетинається з попереднім
        // зарахованим відрізком того ж виду.
        List<StreakKind> expected = new ArrayList<>();
        int good = 0;
        int bad = 0;
        for (int quality : qualities) {
            good = RULES.isVeryGood(quality) ? good + 1 : 0;
            bad = RULES.isVeryBad(quality) ? bad + 1 : 0;
            if (good == RULES.length()) {
                good = 0;
                if (!expected.contains(StreakKind.GOLDEN_AGE)) {
                    expected.add(StreakKind.GOLDEN_AGE);
                }
            }
            if (bad == RULES.length()) {
                bad = 0;
                if (!expected.contains(StreakKind.UNDERDOG)) {
                    expected.add(StreakKind.UNDERDOG);
                }
            }
        }

        assertThat(fired(qualities)).isEqualTo(expected);
    }

    @Property
    void countersStayBelowLengthAndOnlyOneRuns(
            @ForAll @Size(max = 40) List<@IntRange(min = 0, max = 100) Integer> qualities) {
        Streaks streaks = Streaks.START;
        for (int quality : qualities) {
            streaks = streaks.next(RULES, quality).streaks();
            assertThat(streaks.veryGood()).isBetween(0, RULES.length() - 1);
            assertThat(streaks.veryBad()).isBetween(0, RULES.length() - 1);
            assertThat(streaks.veryGood() == 0 || streaks.veryBad() == 0).isTrue();
        }
    }

    private static List<StreakKind> fired(List<Integer> qualities) {
        List<StreakKind> fired = new ArrayList<>();
        Streaks streaks = Streaks.START;
        for (int quality : qualities) {
            Streaks.Step step = streaks.next(RULES, quality);
            Optional<StreakKind> triggered = step.triggered();
            triggered.ifPresent(fired::add);
            streaks = step.streaks();
            assertThat(streaks.fired()).containsAll(fired);
        }
        return fired;
    }
}
