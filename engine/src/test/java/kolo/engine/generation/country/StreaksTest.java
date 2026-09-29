package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRulesDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class StreaksTest {

    /** Дуже добре — від 85, дуже погано — до 15, стрік — 3 поспіль. */
    private static final StreakRulesDef RULES = new StreakRulesDef(85, 15, 3);

    @Test
    void threeVeryGoodInARowTriggerGoldenAge() {
        assertThat(triggers(90, 85, 100)).containsExactly(null, null, StreakKind.GOLDEN_AGE);
    }

    @Test
    void threeVeryBadInARowTriggerUnderdog() {
        assertThat(triggers(15, 0, 10)).containsExactly(null, null, StreakKind.UNDERDOG);
    }

    @Test
    void anyResultOutsideExtremesBreaksTheStreak() {
        // 84 і 16 — вже не крайні результати.
        assertThat(triggers(90, 90, 84, 90, 90)).containsOnlyNulls();
        assertThat(triggers(10, 10, 16, 10, 10)).containsOnlyNulls();
        assertThat(triggers(90, 90, 50, 90, 90, 90))
                .containsExactly(null, null, null, null, null, StreakKind.GOLDEN_AGE);
    }

    @Test
    void oppositeExtremeBreaksTheStreakAndStartsItsOwn() {
        assertThat(triggers(90, 90, 10, 10, 10)).containsExactly(null, null, null, null, StreakKind.UNDERDOG);
        assertThat(triggers(10, 10, 90, 90, 90)).containsExactly(null, null, null, null, StreakKind.GOLDEN_AGE);
    }

    @Test
    void eachKindFiresAtMostOnceAndCounterResets() {
        // Після спрацювання лічильник скидається: четвертий дуже добрий результат не запускає колесо вдруге, а
        // ще три поспіль лише скидають лічильник — «Золота доба» вже була.
        List<StreakKind> kinds = triggers(90, 90, 90, 90, 90, 90, 10, 10, 10, 10, 10, 10);

        assertThat(kinds)
                .containsExactly(
                        null,
                        null,
                        StreakKind.GOLDEN_AGE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        StreakKind.UNDERDOG,
                        null,
                        null,
                        null);
    }

    @Test
    void counterIsResetAfterStreakEvenWhenAlreadyFired() {
        Streaks afterGolden = run(90, 90, 90);
        Streaks.Step next = afterGolden.next(RULES, 90);

        assertThat(afterGolden).isEqualTo(new Streaks(0, 0, new TreeSet<>(Set.of(StreakKind.GOLDEN_AGE))));
        assertThat(next.streaks().veryGood()).isEqualTo(1);
        Streaks third = next.streaks().next(RULES, 90).streaks().next(RULES, 90).streaks();
        assertThat(third.veryGood()).isZero();
        assertThat(third.fired()).containsExactly(StreakKind.GOLDEN_AGE);
    }

    @Test
    void countsFollowTheCurrentRun() {
        assertThat(run(90, 90)).isEqualTo(new Streaks(2, 0, new TreeSet<>()));
        assertThat(run(90, 10)).isEqualTo(new Streaks(0, 1, new TreeSet<>()));
        assertThat(run(10, 50)).isEqualTo(Streaks.START);
    }

    @Test
    void lengthComesFromBalance() {
        StreakRulesDef two = new StreakRulesDef(85, 15, 2);

        assertThat(Streaks.START.next(two, 90).streaks().next(two, 90).triggered())
                .contains(StreakKind.GOLDEN_AGE);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> Streaks.START.next(RULES, 101))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() -> Streaks.START.next(RULES, -1)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Streaks(1, 1, new TreeSet<>()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() -> new Streaks(-1, 0, new TreeSet<>())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Streaks.START.fired().add(StreakKind.UNDERDOG))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** Вид стріку, що спрацював на кожному результаті; {@code null} — жоден. */
    private static List<StreakKind> triggers(int... qualities) {
        List<StreakKind> kinds = new ArrayList<>();
        Streaks streaks = Streaks.START;
        for (int quality : qualities) {
            Streaks.Step step = streaks.next(RULES, quality);
            kinds.add(step.triggered().orElse(null));
            streaks = step.streaks();
        }
        return kinds;
    }

    private static Streaks run(int... qualities) {
        Streaks streaks = Streaks.START;
        for (int quality : qualities) {
            streaks = streaks.next(RULES, quality).streaks();
        }
        return streaks;
    }

    @Test
    void stepRequiresBothParts() {
        assertThatThrownBy(() -> new Streaks.Step(null, Optional.empty())).isInstanceOf(NullPointerException.class);
    }
}
