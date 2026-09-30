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
 * @param personTraits скільки рис у стартової постаті (GD §12.3), {@code 1..}{@value #MAX_TRAITS}
 * @param personAge вік стартової постаті на 1970 рік, {@value #MIN_PERSON_AGE}{@code ..}{@value #MAX_PERSON_AGE}
 * @param nameCandidates скільки назв-кандидатів на колесі назви (GD §4.9), {@code 1..}{@value #MAX_NAME_CANDIDATES}
 * @param developmentPopulationAdvantage перевага колеса розвиненості кожної галузі за кожен крок рівня результату
 *     рівня населення від часткового, {@code −}{@value Advantage#MAX}{@code ..}{@value Advantage#MAX}: додатна —
 *     багатолюдніша держава має більше фахівців
 * @param gdpPopulationAdvantage перевага колеса ВВП на душу за кожен крок рівня результату рівня населення від
 *     часткового, {@code −}{@value Advantage#MAX}{@code ..}{@value Advantage#MAX}: від'ємна — у багатолюднішої
 *     держави більше ротів на той самий продукт
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
        int armyTrainingDevelopmentAdvantage,
        CountRange personTraits,
        CountRange personAge,
        int nameCandidates,
        int developmentPopulationAdvantage,
        int gdpPopulationAdvantage) {

    /** Більше фрагментів чи постатей перевантажили б картку країни. */
    public static final int MAX_COUNT = 10;

    /** Стартовий арсенал — «кілька боєголовок», а не сотні: кожну боєголовку можна застосувати окремо. */
    public static final int MAX_WARHEADS = 100;

    /** Постать має 1–3 риси (GD §12.3). */
    public static final int MAX_TRAITS = 3;

    /** Відома постать — доросла людина. */
    public static final int MIN_PERSON_AGE = 16;

    public static final int MAX_PERSON_AGE = 100;

    /** Більше назв не прочитати на колесі: сектори стали б надто вузькими для підписів. */
    public static final int MAX_NAME_CANDIDATES = 12;

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
        check("generation.person_traits", personTraits, MAX_TRAITS);
        Objects.requireNonNull(personAge, "generation.person_age");
        Checks.inRange("generation.person_age.min", personAge.min(), MIN_PERSON_AGE, MAX_PERSON_AGE);
        Checks.inRange("generation.person_age.max", personAge.max(), personAge.min(), MAX_PERSON_AGE);
        Checks.inRange("generation.name_candidates", nameCandidates, 1, MAX_NAME_CANDIDATES);
        Checks.inRange(
                "generation.development_population_advantage",
                developmentPopulationAdvantage,
                -Advantage.MAX,
                Advantage.MAX);
        Checks.inRange("generation.gdp_population_advantage", gdpPopulationAdvantage, -Advantage.MAX, Advantage.MAX);
    }

    private static void check(String field, CountRange range, int max) {
        Objects.requireNonNull(range, field);
        Checks.inRange(field + ".min", range.min(), 1, max);
        Checks.inRange(field + ".max", range.max(), range.min(), max);
    }
}
