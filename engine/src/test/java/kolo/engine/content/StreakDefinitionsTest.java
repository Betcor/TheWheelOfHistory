package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.FateTokens;
import kolo.engine.state.Stat;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class StreakDefinitionsTest {

    private static final List<ModifierDef> STABILITY =
            List.of(new ModifierDef(ModifierTarget.stat(Stat.STABILITY), 10));

    @Test
    void streakKindsHaveContentKeys() {
        assertThat(StreakKind.GOLDEN_AGE.key()).isEqualTo("golden_age");
        assertThat(StreakKind.UNDERDOG.key()).isEqualTo("underdog");
    }

    @Test
    void rewardKeepsItsEffects() {
        StreakRewardDef reward = reward(100, 10, STABILITY, 1, 2, List.of("sympathy"));

        assertThat(reward.weight()).isEqualTo(100);
        assertThat(reward.durationYears()).isEqualTo(10);
        assertThat(reward.modifiers()).isEqualTo(STABILITY);
        assertThat(reward.fateTokens()).isEqualTo(1);
        assertThat(reward.extraPeople()).isEqualTo(2);
        assertThat(reward.tags()).containsExactly("sympathy");
    }

    @Test
    void rewardNumbersAreWithinLimits() {
        assertOutOfRange(() -> reward(0, 0, STABILITY, 0, 0, List.of()), "streak_reward.pride.weight");
        assertOutOfRange(
                () -> reward(StreakRewardDef.MAX_WEIGHT + 1, 0, STABILITY, 0, 0, List.of()),
                "streak_reward.pride.weight");
        assertOutOfRange(() -> reward(100, -1, STABILITY, 0, 0, List.of()), "streak_reward.pride.duration");
        assertOutOfRange(
                () -> reward(100, StreakRewardDef.MAX_DURATION_YEARS + 1, STABILITY, 0, 0, List.of()),
                "streak_reward.pride.duration");
        assertOutOfRange(
                () -> reward(100, 0, List.of(), FateTokens.MAX + 1, 0, List.of()), "streak_reward.pride.fate_tokens");
        assertOutOfRange(() -> reward(100, 0, List.of(), -1, 0, List.of()), "streak_reward.pride.fate_tokens");
        assertOutOfRange(
                () -> reward(100, 0, List.of(), 0, StreakRewardDef.MAX_EXTRA_PEOPLE + 1, List.of()),
                "streak_reward.pride.extra_people");
    }

    @Test
    void anySingleEffectMakesARewardValid() {
        assertThat(reward(100, 0, STABILITY, 0, 0, List.of()).modifiers()).hasSize(1);
        assertThat(reward(100, 0, List.of(), FateTokens.MAX, 0, List.of()).fateTokens())
                .isEqualTo(FateTokens.MAX);
        assertThat(reward(100, 0, List.of(), 0, 1, List.of()).extraPeople()).isEqualTo(1);
        assertThat(reward(100, 0, List.of(), 0, 0, List.of("sympathy")).tags()).containsExactly("sympathy");
    }

    @Test
    void rewardWithoutEffectsIsRejected() {
        assertThatThrownBy(() -> reward(100, 10, List.of(), 0, 0, List.of()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.EMPTY_COLLECTION);
                    assertThat(e.details()).containsExactly(entry("field", "streak_reward.pride.effects"));
                });
    }

    @Test
    void rewardTextsAndTagsAreChecked() {
        assertFails(
                () -> new StreakRewardDef(new StreakRewardId("pride"), " ", "Опис", 100, 0, STABILITY, 0, 0, List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new StreakRewardDef(
                        new StreakRewardId("pride"), "Гордість", "", 100, 0, STABILITY, 0, 0, List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(() -> reward(100, 0, STABILITY, 0, 0, List.of("Bad Tag")), ErrorCode.INVALID_KEY_FORMAT);
        assertFails(() -> reward(100, 0, STABILITY, 0, 0, List.of("a", "a")), ErrorCode.DUPLICATE_ID);
        assertFails(() -> new StreakRewardId("Pride"), ErrorCode.INVALID_KEY_FORMAT);
    }

    @Test
    void wheelLooksUpRewardsAndKeepsContentOrder() {
        StreakRewardDef first = TestContent.reward("pride", List.of());
        StreakRewardDef second = TestContent.reward("prestige", List.of());
        StreakWheelDef wheel =
                TestContent.streakWheel(StreakKind.GOLDEN_AGE, List.of("world_attention"), first, second);

        assertThat(wheel.rewards()).containsExactly(first, second);
        assertThat(wheel.reward(new StreakRewardId("prestige"))).contains(second);
        assertThat(wheel.reward(new StreakRewardId("missing"))).isEmpty();
        assertThat(wheel.tags()).containsExactly("world_attention");
    }

    @Test
    void wheelNeedsUniqueRewards() {
        assertThatThrownBy(() -> TestContent.streakWheel(StreakKind.UNDERDOG, List.of()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.EMPTY_COLLECTION);
                    assertThat(e.details()).containsExactly(entry("field", "streak.underdog.rewards"));
                });
        assertThatThrownBy(() -> TestContent.streakWheel(
                        StreakKind.UNDERDOG,
                        List.of(),
                        TestContent.reward("pride", List.of()),
                        TestContent.reward("pride", List.of("other"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details())
                            .containsExactly(entry("field", "streak.underdog.rewards"), entry("value", "pride"));
                });
        assertFails(
                () -> new StreakWheelDef(
                        StreakKind.UNDERDOG, "", "Опис", List.of(), List.of(TestContent.reward("pride", List.of()))),
                ErrorCode.BLANK_VALUE);
    }

    @Test
    void contentDefinesEveryKindOnce() {
        StreakWheelDef golden =
                TestContent.streakWheel(StreakKind.GOLDEN_AGE, List.of(), TestContent.reward("pride", List.of()));
        StreakWheelDef underdog =
                TestContent.streakWheel(StreakKind.UNDERDOG, List.of(), TestContent.reward("chance", List.of()));

        // Порядок у контенті не важливий: колеса впорядковано за видом.
        assertThat(new StreakContent(List.of(underdog, golden)).wheels().values())
                .containsExactly(golden, underdog);
        assertThatThrownBy(() -> new StreakContent(List.of(golden)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION);
                    assertThat(e.details()).containsExactly(entry("field", "streaks"), entry("value", "underdog"));
                });
        assertThatThrownBy(() -> new StreakContent(List.of(golden, underdog, golden)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details()).containsExactly(entry("field", "streaks"), entry("value", "golden_age"));
                });
    }

    @Test
    void producedTagsJoinWheelAndRewardTags() {
        StreakContent content = TestContent.streaks();

        assertThat(content.producedTags()).containsExactly("international_sympathy", "world_attention");
        assertThat(content.wheel(StreakKind.GOLDEN_AGE).kind()).isEqualTo(StreakKind.GOLDEN_AGE);
        assertThatThrownBy(() -> content.wheels().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static StreakRewardDef reward(
            int weight, int duration, List<ModifierDef> modifiers, int fateTokens, int extraPeople, List<String> tags) {
        return new StreakRewardDef(
                new StreakRewardId("pride"),
                "Гордість",
                "Опис",
                weight,
                duration,
                modifiers,
                fateTokens,
                extraPeople,
                tags);
    }

    private static void assertOutOfRange(ThrowingCallable create, String field) {
        assertThatThrownBy(create).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
            assertThat(e.details()).contains(entry("field", field));
        });
    }

    private static void assertFails(ThrowingCallable create, ErrorCode code) {
        assertThatThrownBy(create)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
