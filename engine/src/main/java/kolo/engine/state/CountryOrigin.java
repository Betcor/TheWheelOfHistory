package kolo.engine.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.HdiLevelId;
import kolo.engine.content.PopulationLevelId;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardId;
import kolo.engine.error.Checks;

/**
 * Як держава з'явилася: результати коліс генерації, що не стали окремими показниками стану, — для картки держави,
 * хроніки й енциклопедії. Не змінюється.
 *
 * @param area рівень площі
 * @param population рівень населення
 * @param gdp рівень ВВП на душу
 * @param hdi рівень ІЛР
 * @param armySize рівень розміру армії
 * @param backstory фрагменти передісторії в хронологічному порядку
 * @param backstoryNeighbor сусід, якого згадує передісторія
 * @param streaks стріки генерації в порядку спрацювання
 * @param corridor коридор бюджету сили, з яким генерувалася держава
 * @param strengthPct підсумкова сила у відсотках медіани
 */
public record CountryOrigin(
        AreaLevelId area,
        PopulationLevelId population,
        GdpLevelId gdp,
        HdiLevelId hdi,
        ArmySizeId armySize,
        List<Backstory> backstory,
        Optional<CountryId> backstoryNeighbor,
        List<Streak> streaks,
        PowerCorridor corridor,
        int strengthPct) {

    public CountryOrigin {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(population, "population");
        Objects.requireNonNull(gdp, "gdp");
        Objects.requireNonNull(hdi, "hdi");
        Objects.requireNonNull(armySize, "armySize");
        backstory = List.copyOf(backstory);
        Objects.requireNonNull(backstoryNeighbor, "backstoryNeighbor");
        streaks = List.copyOf(streaks);
        Objects.requireNonNull(corridor, "corridor");
        Checks.inRange("strength_pct", strengthPct, 0, Integer.MAX_VALUE);
    }

    /** Фрагмент передісторії з роком. */
    public record Backstory(BackstoryFragmentId fragment, int year) {

        public Backstory {
            Objects.requireNonNull(fragment, "fragment");
        }
    }

    /** Стрік генерації й нагорода з його колеса. */
    public record Streak(StreakKind kind, StreakRewardId reward) {

        public Streak {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(reward, "reward");
        }
    }
}
