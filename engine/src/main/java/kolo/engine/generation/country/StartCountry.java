package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.modifier.Modifier;
import kolo.engine.state.FateTokens;
import kolo.engine.wheel.RollRecord;

/**
 * Держава, зібрана з ланцюжка коліс генерації (GD §4.1) — результат {@link CountryGenerator}. Колеса карти
 * (материк, площа, географія, населення, ресурси) й доктрини сюди ще не входять.
 *
 * @param religion державна релігія або світська держава (колесо 6а)
 * @param streaks нагороди стріків у порядку спрацювання; кожен вид — щонайбільше раз
 * @param people відомі люди разом із додатковими постатями нагород стріків
 * @param tags усі мітки держави: ладу, релігії, рівнів коліс, стріків і передісторії
 * @param modifiers модифікатори держави в порядку набуття: лад, релігія, стріки, передісторія
 * @param fateTokens жетони долі з нагород стріків, обрізані до {@link FateTokens#MAX}
 * @param rolls усі обертання в порядку кидків, разом з колесами стріків на їхньому місці
 */
public record StartCountry(
        Regime regime,
        StartStateReligion religion,
        StartDevelopment development,
        StartGdp gdp,
        StartHdi hdi,
        StartArmySize armySize,
        StartArmyTraining armyTraining,
        StartNuclear nuclear,
        Backstory backstory,
        List<StreakBonus> streaks,
        StartName name,
        StartPeople people,
        SortedSet<String> tags,
        List<Modifier> modifiers,
        int fateTokens,
        List<RollRecord> rolls) {

    public StartCountry {
        Objects.requireNonNull(regime, "regime");
        Objects.requireNonNull(religion, "religion");
        Objects.requireNonNull(development, "development");
        Objects.requireNonNull(gdp, "gdp");
        Objects.requireNonNull(hdi, "hdi");
        Objects.requireNonNull(armySize, "armySize");
        Objects.requireNonNull(armyTraining, "armyTraining");
        Objects.requireNonNull(nuclear, "nuclear");
        Objects.requireNonNull(backstory, "backstory");
        streaks = List.copyOf(streaks);
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(people, "people");
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        modifiers = List.copyOf(modifiers);
        Checks.inRange("fate_tokens", fateTokens, 0, FateTokens.MAX);
        rolls = List.copyOf(rolls);
    }
}
