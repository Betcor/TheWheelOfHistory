package kolo.engine.modifier;

import static kolo.engine.modifier.TestModifiers.BATTLE;
import static kolo.engine.modifier.TestModifiers.CONSTRUCTION;
import static kolo.engine.modifier.TestModifiers.onStat;
import static kolo.engine.modifier.TestModifiers.onWheel;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.state.CountryStats;
import kolo.engine.state.Stat;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import org.junit.jupiter.api.Test;

class ModifiersTest {

    private static final CountryStats BASE = new CountryStats(10_000, 60, 50, 30, 70, 5, 20);

    @Test
    void noModifiersMeansBaseValues() {
        assertThat(Modifiers.effective(BASE, List.of(), 0)).isEqualTo(BASE);
    }

    @Test
    void breakdownListsActiveContributionsInOrder() {
        List<Modifier> modifiers = List.of(
                onStat("reform", Stat.STABILITY, 8, null),
                onStat("hdi_bonus", Stat.HDI, 3, null),
                onStat("scandal", Stat.STABILITY, -15, 4),
                onStat("old_war", Stat.STABILITY, -30, 2));

        StatBreakdown breakdown = Modifiers.breakdown(BASE, modifiers, Stat.STABILITY, 3);

        assertThat(breakdown.base()).isEqualTo(60);
        assertThat(breakdown.contributions())
                .containsExactly(
                        new AppliedModifier("reform", "modifier.reform", 8),
                        new AppliedModifier("scandal", "modifier.scandal", -15));
        assertThat(breakdown.effective()).isEqualTo(53);
        assertThat(breakdown.clamped()).isFalse();
    }

    @Test
    void effectiveIsClampedToStatRange() {
        List<Modifier> modifiers = List.of(
                onStat("golden_age", Stat.STABILITY, 70, null),
                onStat("collapse", Stat.HDI, -90, null),
                onStat("crash", Stat.GDP, -50_000, null));

        CountryStats effective = Modifiers.effective(BASE, modifiers, 0);

        assertThat(effective.stability()).isEqualTo(100);
        assertThat(effective.hdi()).isZero();
        assertThat(effective.gdp()).isZero();
        assertThat(effective.influence()).isEqualTo(30);
        assertThat(Modifiers.breakdown(BASE, modifiers, Stat.STABILITY, 0).clamped())
                .isTrue();
    }

    @Test
    void statModifiersDoNotLeakIntoOtherStats() {
        CountryStats effective = Modifiers.effective(BASE, List.of(onStat("sci", Stat.SCIENCE, 7, null)), 0);

        assertThat(effective).isEqualTo(new CountryStats(10_000, 60, 50, 30, 70, 5, 27));
    }

    @Test
    void wheelModifiersAffectOnlyTheirWheelKind() {
        List<Modifier> modifiers = List.of(
                onWheel("engineers", CONSTRUCTION, 15, null),
                onWheel("veterans", BATTLE, 20, null),
                onWheel("corruption", CONSTRUCTION, -5, null),
                onStat("reform", Stat.STABILITY, 10, null));

        Advantage advantage = Modifiers.advantage(modifiers, CONSTRUCTION, 0);

        assertThat(advantage.value()).isEqualTo(10);
        assertThat(advantage.modifiers())
                .containsExactly(
                        new AppliedModifier("engineers", "modifier.engineers", 15),
                        new AppliedModifier("corruption", "modifier.corruption", -5));
    }

    @Test
    void advantageIsClampedButKeepsAllExplanations() {
        List<Modifier> modifiers = List.of(onWheel("a", CONSTRUCTION, 80, null), onWheel("b", CONSTRUCTION, 70, null));

        Advantage advantage = Modifiers.advantage(modifiers, CONSTRUCTION, 0);

        assertThat(advantage.value()).isEqualTo(Advantage.MAX);
        assertThat(advantage.modifiers()).hasSize(2);
    }

    @Test
    void expiredModifiersAreIgnored() {
        List<Modifier> modifiers = List.of(onWheel("festival", CONSTRUCTION, 25, 1));

        assertThat(Modifiers.advantage(modifiers, CONSTRUCTION, 1).value()).isEqualTo(25);
        assertThat(Modifiers.advantage(modifiers, CONSTRUCTION, 2)).isEqualTo(Advantage.NONE);
    }

    @Test
    void withoutExpiredRemovesModifiersEndingThisTurnAndKeepsOrder() {
        Modifier permanent = onStat("permanent", Stat.HDI, 1, null);
        Modifier endsNow = onStat("ends_now", Stat.HDI, 2, 5);
        Modifier endsLater = onStat("ends_later", Stat.HDI, 3, 6);
        Modifier alreadyGone = onStat("gone", Stat.HDI, 4, 2);

        assertThat(Modifiers.withoutExpired(List.of(endsLater, endsNow, alreadyGone, permanent), 5))
                .containsExactly(endsLater, permanent);
    }
}
