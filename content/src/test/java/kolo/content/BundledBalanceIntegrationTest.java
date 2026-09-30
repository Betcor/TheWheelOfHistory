package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.BalanceDef;
import kolo.engine.content.MedianRange;
import kolo.engine.content.PowerCorridorDef;
import kolo.engine.state.PowerCorridor;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

/** Вбудований баланс ↔ рушій: числа відповідають дизайну й працюють із колесом. */
class BundledBalanceIntegrationTest {

    private static final BalanceDef BALANCE = ContentLoader.loadBundled().balance();

    @Test
    void streaksMatchDesign() {
        // GD §4.10: дуже добрий — якість ≥ 70, дуже поганий — ≤ 30, стрік — 3 поспіль.
        assertThat(BALANCE.streaks().veryGoodQuality()).isEqualTo(70);
        assertThat(BALANCE.streaks().veryBadQuality()).isEqualTo(30);
        assertThat(BALANCE.streaks().length()).isEqualTo(3);
    }

    @Test
    void corridorsMatchDesign() {
        // GD §4.11: «класика» — від 0,75× до 1,33× медіани (під шкалу сили, §24.1); для NPC коридор ширший.
        assertThat(BALANCE.corridor(PowerCorridor.CLASSIC).players()).isEqualTo(new MedianRange(75, 133));
        for (PowerCorridorDef corridor : BALANCE.corridors().values()) {
            assertThat(corridor.npc().contains(corridor.players())).isTrue();
            assertThat(corridor.npc()).as(corridor.corridor().key()).isNotEqualTo(corridor.players());
        }
        // Варіанти впорядковано від найвужчого до найширшого, як їх показує лобі.
        List<PowerCorridorDef> ordered = List.copyOf(BALANCE.corridors().values());
        for (int i = 1; i < ordered.size(); i++) {
            assertThat(ordered.get(i).players().contains(ordered.get(i - 1).players()))
                    .isTrue();
            assertThat(ordered.get(i).npc().contains(ordered.get(i - 1).npc())).isTrue();
        }
    }

    @Test
    void generationCountsMatchDesign() {
        // GD §4.7: 2–4 фрагменти передісторії; GD §4.8: 1–3 постаті.
        assertThat(BALANCE.generation().backstoryFragments().min()).isEqualTo(2);
        assertThat(BALANCE.generation().backstoryFragments().max()).isEqualTo(4);
        assertThat(BALANCE.generation().notablePeople().min()).isEqualTo(1);
        assertThat(BALANCE.generation().notablePeople().max()).isEqualTo(3);
    }

    @Test
    void fullInvestmentDoesNotGuaranteeMaximumAdvantage() {
        // Вкладення — лише одне з джерел переваги (GD §2.3): самі по собі не дають +100.
        int full = BALANCE.wheel().investmentAdvantage(BALANCE.wheel().maxInvestments());

        assertThat(full).isPositive().isLessThan(Advantage.MAX);
    }

    @Test
    void balancedStrengthKeepsBothCriticalSectorsOnStandardWheel() {
        // GD §2.2: типове колесо будівництва; при будь-якій крайній перевазі КП і КУ лишаються ≥ 1%.
        List<Sector<String>> construction = List.of(
                new Sector<>("crit_fail", 300, "crit_fail", 0, OutcomeTier.CRIT_FAIL, List.of()),
                new Sector<>("fail", 1200, "fail", 20, OutcomeTier.FAIL, List.of()),
                new Sector<>("partial", 2500, "partial", 50, OutcomeTier.PARTIAL, List.of()),
                new Sector<>("success", 5000, "success", 75, OutcomeTier.SUCCESS, List.of()),
                new Sector<>("crit_success", 1000, "crit_success", 100, OutcomeTier.CRIT_SUCCESS, List.of()));
        int strength = BALANCE.wheel().strength(new WheelKind("construction"));

        for (int advantage : List.of(Advantage.MIN, 0, Advantage.MAX)) {
            List<Sector<String>> result = Wheel.applyAdvantage(construction, advantage, strength);

            assertThat(result.stream().mapToInt(Sector::weightBp).sum()).isEqualTo(Wheel.TOTAL_BP);
            assertThat(result.getFirst().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
            assertThat(result.getLast().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
            // Сила < 100: навіть при −100 успіх можливий, при +100 провал можливий.
            assertThat(result.get(1).weightBp()).isPositive();
            assertThat(result.get(3).weightBp()).isPositive();
        }
    }
}
