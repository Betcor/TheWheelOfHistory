package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.modifier.Modifier;
import kolo.engine.state.FateTokens;
import kolo.engine.util.Fixed;
import kolo.engine.wheel.RollRecord;

/**
 * Держава, зібрана з ланцюжка коліс генерації (GD §4.1) — результат {@link CountryGenerator}. Доктрини сюди ще не
 * входять.
 *
 * @param territory материк, площа й провінції держави (колеса 1–2)
 * @param geography вихід до моря й місцевість (3)
 * @param population населення та його розподіл по провінціях (4)
 * @param religion державна релігія або світська держава (колесо 6а)
 * @param resources родовища (13)
 * @param streaks нагороди стріків у порядку спрацювання; кожен вид — щонайбільше раз
 * @param power бюджет сили (GD §4.11): сила після кожного складника й зсуви коридору
 * @param people відомі люди разом із додатковими постатями нагород стріків
 * @param tags усі мітки держави: площі, географії, населення, ладу, релігії, рівнів коліс, стріків і передісторії
 * @param modifiers модифікатори держави в порядку набуття: лад, релігія, стріки, передісторія
 * @param fateTokens жетони долі з нагород стріків, обрізані до {@link FateTokens#MAX}
 * @param rolls усі обертання в порядку кидків — від коліс материка й площі до людей, з колесами стріків на їхньому
 *     місці
 */
public record StartCountry(
        PlacedCountry territory,
        StartGeography geography,
        StartPopulation population,
        Regime regime,
        StartStateReligion religion,
        StartDevelopment development,
        StartGdp gdp,
        StartHdi hdi,
        StartArmySize armySize,
        StartArmyTraining armyTraining,
        StartResources resources,
        StartNuclear nuclear,
        Backstory backstory,
        List<StreakBonus> streaks,
        PowerBudget power,
        StartName name,
        StartPeople people,
        SortedSet<String> tags,
        List<Modifier> modifiers,
        int fateTokens,
        List<RollRecord> rolls) {

    /** Населення рахується в тисячах. */
    private static final long THOUSAND = 1_000;

    public StartCountry {
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(geography, "geography");
        Objects.requireNonNull(population, "population");
        Objects.requireNonNull(regime, "regime");
        Objects.requireNonNull(religion, "religion");
        Objects.requireNonNull(development, "development");
        Objects.requireNonNull(gdp, "gdp");
        Objects.requireNonNull(hdi, "hdi");
        Objects.requireNonNull(armySize, "armySize");
        Objects.requireNonNull(armyTraining, "armyTraining");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(nuclear, "nuclear");
        Objects.requireNonNull(backstory, "backstory");
        streaks = List.copyOf(streaks);
        Objects.requireNonNull(power, "power");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(people, "people");
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        modifiers = List.copyOf(modifiers);
        Checks.inRange("fate_tokens", fateTokens, 0, FateTokens.MAX);
        rolls = List.copyOf(rolls);
    }

    /** Загальний ВВП (GD §5.1): ВВП на душу × населення, умовні долари 1970 року за рік. */
    public long totalGdp() {
        return Math.multiplyExact(Math.multiplyExact((long) gdp.perCapita(), population.populationK()), THOUSAND);
    }

    /** Чисельність армії (GD §4.4): частка населення під зброєю, людей, вниз. */
    public long armyStrength() {
        return Fixed.mulDiv(
                Math.multiplyExact((long) population.populationK(), THOUSAND), armySize.shareBp(), Fixed.BP_SCALE);
    }
}
