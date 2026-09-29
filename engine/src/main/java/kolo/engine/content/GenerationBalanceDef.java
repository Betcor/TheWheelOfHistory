package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.wheel.Advantage;

/**
 * Числа ланцюжка коліс генерації країни (GD §4.1).
 *
 * @param backstoryFragments скільки фрагментів передісторії (GD §4.7), не менше одного
 * @param notablePeople скільки відомих людей на старті (GD §4.8), не менше одного
 * @param warheads скільки боєголовок у стартовому арсеналі (GD §4.6), {@code 1..}{@value #MAX_WARHEADS}
 * @param nuclearEnergyAdvantage перевага колеса ядерного статусу за кожен рівень розвиненості енергетики й науки
 *     відносно світового (GD §4.6), {@code 0..}{@value Advantage#MAX}: рівень {@code −1} дає мінус стільки, {@code
 *     +2} — удвічі більше
 * @param gdpDevelopmentAdvantage перевага колеса ВВП за кожен рівень розвиненості економіки й суспільства
 *     відносно світового (окремо за кожну з двох галузей), {@code 0..}{@value Advantage#MAX}
 * @param hdiGdpAdvantage перевага колеса ІЛР за кожен крок рівня результату рівня ВВП від часткового ({@link
 *     kolo.engine.wheel.OutcomeTier#step()}), {@code 0..}{@value Advantage#MAX}: найбідніший рівень дає {@code
 *     −2·x}, найбагатший — {@code +2·x}
 * @param armySizeGdpAdvantage перевага колеса розміру армії за кожен крок рівня результату рівня ВВП від часткового,
 *     {@code 0..}{@value Advantage#MAX}: багатша держава утримує більшу армію
 * @param armyTrainingGdpAdvantage перевага колеса вишколу армії за кожен крок рівня результату рівня ВВП від
 *     часткового, {@code 0..}{@value Advantage#MAX}: багатша держава більше витрачає на навчання
 * @param armyTrainingDevelopmentAdvantage перевага колеса вишколу армії за кожен рівень розвиненості військової
 *     галузі відносно світового, {@code 0..}{@value Advantage#MAX}
 */
public record GenerationBalanceDef(
        CountRange backstoryFragments,
        CountRange notablePeople,
        CountRange warheads,
        int nuclearEnergyAdvantage,
        int gdpDevelopmentAdvantage,
        int hdiGdpAdvantage,
        int armySizeGdpAdvantage,
        int armyTrainingGdpAdvantage,
        int armyTrainingDevelopmentAdvantage) {

    /** Більше фрагментів чи постатей перевантажили б картку країни. */
    public static final int MAX_COUNT = 10;

    /** Стартовий арсенал — «кілька боєголовок», а не сотні: кожну боєголовку можна застосувати окремо. */
    public static final int MAX_WARHEADS = 100;

    public GenerationBalanceDef {
        check("generation.backstory_fragments", backstoryFragments, MAX_COUNT);
        check("generation.notable_people", notablePeople, MAX_COUNT);
        check("generation.warheads", warheads, MAX_WARHEADS);
        Checks.inRange("generation.nuclear_energy_advantage", nuclearEnergyAdvantage, 0, Advantage.MAX);
        Checks.inRange("generation.gdp_development_advantage", gdpDevelopmentAdvantage, 0, Advantage.MAX);
        Checks.inRange("generation.hdi_gdp_advantage", hdiGdpAdvantage, 0, Advantage.MAX);
        Checks.inRange("generation.army_size_gdp_advantage", armySizeGdpAdvantage, 0, Advantage.MAX);
        Checks.inRange("generation.army_training_gdp_advantage", armyTrainingGdpAdvantage, 0, Advantage.MAX);
        Checks.inRange(
                "generation.army_training_development_advantage", armyTrainingDevelopmentAdvantage, 0, Advantage.MAX);
    }

    private static void check(String field, CountRange range, int max) {
        Objects.requireNonNull(range, field);
        Checks.inRange(field + ".min", range.min(), 1, max);
        Checks.inRange(field + ".max", range.max(), range.min(), max);
    }
}
