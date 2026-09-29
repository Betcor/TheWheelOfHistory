package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestStreaks.FIGURE;
import static kolo.engine.generation.country.TestStreaks.PRESTIGE;
import static kolo.engine.generation.country.TestStreaks.PRIDE;
import static kolo.engine.generation.country.TestStreaks.SECOND_CHANCE;
import static kolo.engine.generation.country.TestStreaks.SYMPATHY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.rng.Rng;
import kolo.engine.state.Stat;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StreakWheelTest {

    private static final ContentPack PACK = TestRegime.PACK;
    private static final int SEEDS = 500;

    @Test
    void wheelKindsAreNamedAfterStreaks() {
        assertThat(StreakWheel.kind(StreakKind.GOLDEN_AGE)).isEqualTo(new WheelKind("generation_golden_age"));
        assertThat(StreakWheel.kind(StreakKind.UNDERDOG)).isEqualTo(new WheelKind("generation_underdog"));
    }

    @Test
    void sectorsFollowContentOrderAndWeights() {
        List<Sector<StreakRewardDef>> sectors = StreakWheel.sectors(TestStreaks.GOLDEN_AGE);

        assertThat(sectors).extracting(Sector::id).containsExactly("national_pride", "world_prestige", "great_figure");
        assertThat(sectors).extracting(Sector::weightBp).containsExactly(100, 300, 600);
        assertThat(sectors).extracting(Sector::value).containsExactly(PRIDE, PRESTIGE, FIGURE);
        assertThat(sectors).allSatisfy(sector -> {
            assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL);
            assertThat(sector.quality()).isEqualTo(StreakWheel.QUALITY);
            assertThat(sector.tags()).isEqualTo(sector.value().tags());
        });
    }

    @Test
    void rewardComesFromTheStreakWheelAndMatchesTheRoll() {
        for (StreakKind kind : StreakKind.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                StreakBonus bonus = StreakWheel.generate(Rng.of(seed), PACK, kind);

                assertThat(bonus.streak()).isEqualTo(kind);
                assertThat(PACK.streaks().wheel(kind).rewards()).contains(bonus.reward());
                assertThat(bonus.roll().kind()).isEqualTo(StreakWheel.kind(kind));
                assertThat(bonus.roll().resultSectorId())
                        .isEqualTo(bonus.reward().id().value());
            }
        }
    }

    @Test
    void rollHasNoAdvantageAndNormalizedWeights() {
        RollRecord roll =
                StreakWheel.generate(Rng.of(3), PACK, StreakKind.GOLDEN_AGE).roll();

        assertThat(roll.advantage()).isZero();
        assertThat(roll.modifiers()).isEmpty();
        assertThat(roll.turn()).isZero();
        assertThat(roll.season()).isNull();
        assertThat(roll.sectors()).extracting(RolledSector::weightBp).containsExactly(1000, 3000, 6000);
    }

    @Test
    void tagsJoinWheelAndRewardTagsWithoutRepeats() {
        // Нагорода «співчуття» дає й мітку golden_age: у наборі вона одна, разом з underdog колеса.
        StreakBonus bonus = bonus(StreakKind.UNDERDOG, SYMPATHY);

        assertThat(bonus.tags()).containsExactly("golden_age", "international_sympathy", "underdog");
        assertThat(bonus(StreakKind.GOLDEN_AGE, PRIDE).tags()).containsExactly("golden_age", "world_attention");
    }

    @Test
    void modifiersLastDurationYearsFromStart() {
        List<Modifier> modifiers = bonus(StreakKind.GOLDEN_AGE, PRIDE).modifiers();
        ModifierSource source = new ModifierSource(SourceKind.STREAK, "golden_age:national_pride");

        assertThat(modifiers)
                .containsExactly(
                        new Modifier(
                                "streak:golden_age:national_pride:0",
                                source,
                                ModifierTarget.stat(Stat.STABILITY),
                                10,
                                9,
                                "streak.golden_age.national_pride"),
                        new Modifier(
                                "streak:golden_age:national_pride:1",
                                source,
                                ModifierTarget.stat(Stat.LEGITIMACY),
                                5,
                                9,
                                "streak.golden_age.national_pride"));
        // 10 років від 01.01.1970: діє з 1970 по 1979 рік включно.
        assertThat(modifiers.getFirst().isActiveAt(9)).isTrue();
        assertThat(modifiers.getFirst().isActiveAt(10)).isFalse();
    }

    @Test
    void zeroDurationIsPermanentAndOneYearEndsAtFirstTurn() {
        assertThat(bonus(StreakKind.GOLDEN_AGE, PRESTIGE).modifiers())
                .singleElement()
                .satisfies(modifier -> assertThat(modifier.expiresAtTurn()).isNull());
        assertThat(bonus(StreakKind.UNDERDOG, SYMPATHY).modifiers())
                .singleElement()
                .satisfies(modifier -> assertThat(modifier.expiresAtTurn()).isZero());
        assertThat(bonus(StreakKind.GOLDEN_AGE, FIGURE).modifiers()).isEmpty();
    }

    @Test
    void fateTokensAndExtraPeopleComeFromReward() {
        assertThat(bonus(StreakKind.UNDERDOG, SECOND_CHANCE).fateTokens()).isEqualTo(2);
        assertThat(bonus(StreakKind.UNDERDOG, SECOND_CHANCE).extraPeople()).isZero();
        assertThat(bonus(StreakKind.GOLDEN_AGE, FIGURE).extraPeople()).isEqualTo(1);
        assertThat(bonus(StreakKind.GOLDEN_AGE, FIGURE).fateTokens()).isZero();
    }

    @Test
    void rewardStreamIsForkedFromTheGivenStream() {
        int direct = Rng.of(42).fork("reward").nextInt(Wheel.TOTAL_BP);

        assertThat(StreakWheel.generate(Rng.of(42), PACK, StreakKind.UNDERDOG)
                        .roll()
                        .roll())
                .isEqualTo(direct);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageDoesNotChangeStreakWheels(int advantage) {
        // Сектори стріків — PARTIAL: навіть гранична перевага лишає ваги контенту.
        assertThat(Wheel.applyAdvantage(StreakWheel.sectors(TestStreaks.GOLDEN_AGE), advantage, Wheel.MAX_STRENGTH))
                .extracting(Sector::weightBp)
                .containsExactly(1000, 3000, 6000);
        assertThat(Wheel.applyAdvantage(StreakWheel.sectors(TestStreaks.UNDERDOG), advantage, Wheel.MAX_STRENGTH))
                .extracting(Sector::weightBp)
                .containsExactly(5000, 5000);
    }

    @Test
    void rejectsMissingInput() {
        assertThatThrownBy(() -> StreakWheel.generate(Rng.of(1), PACK, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StreakWheel.generate(null, PACK, StreakKind.UNDERDOG))
                .isInstanceOf(NullPointerException.class);
    }

    /** Нагорода з записом справжнього обертання, в якому випав її сектор. */
    private static StreakBonus bonus(StreakKind kind, StreakRewardDef reward) {
        for (long seed = 0; ; seed++) {
            StreakBonus bonus = StreakWheel.generate(Rng.of(seed), PACK, kind);
            if (bonus.reward().equals(reward)) {
                return new StreakBonus(kind, reward, new TreeSet<>(bonus.tags()), bonus.roll());
            }
        }
    }
}
