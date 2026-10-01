package kolo.engine.view;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.ResourceId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TraitId;
import kolo.engine.error.Checks;
import kolo.engine.modifier.Modifier;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.Religion;
import kolo.engine.state.Sex;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.RollRecord;

/**
 * Картка держави гравця: що дала їй генерація (GD §4.12) і записи коліс генерації. Лише власникові — тут розвиненість,
 * ВВП, армія й ядерний статус, які туман війни (GD §16.1) ховає від інших. Назви з контенту (лад, родовища, риси,
 * сектори коліс) — id: клієнт має той самий контент (хеш звірено при з'єднанні).
 *
 * @param number номер держави на карті
 * @param name назва
 * @param capital комірка столиці
 * @param provinces скільки провінцій
 * @param populationK населення, тис. осіб
 * @param ideology ідеологія
 * @param subIdeology підкласифікація
 * @param religion державна релігія; порожньо — світська держава
 * @param worldReligions назви релігій світу в порядку їх генерації: сектори {@code religion_<n>} колеса державної
 *     релігії (релігії світу відомі всім)
 * @param development стартова розвиненість за галузями
 * @param gdpPerCapita ВВП на душу, $ 1970 року
 * @param hdi ІЛР
 * @param armyShareBp частка населення під зброєю, bp
 * @param training вишкіл армії
 * @param nuclear ядерний статус
 * @param warheads боєголовки
 * @param fateTokens жетони долі
 * @param deposits родовища за комірками
 * @param people відомі люди держави в порядку генерації
 * @param origin звідки держава взялася: рівні коліс, передісторія, стріки, сила
 * @param modifiers модифікатори держави
 * @param tags мітки держави
 * @param rolls записи коліс генерації в порядку обертання
 */
public record CountryCard(
        int number,
        LocalizedName name,
        int capital,
        int provinces,
        long populationK,
        IdeologyId ideology,
        SubIdeologyId subIdeology,
        Optional<Religion> religion,
        List<NounPhrase> worldReligions,
        Map<TechBranch, Integer> development,
        int gdpPerCapita,
        int hdi,
        int armyShareBp,
        int training,
        NuclearStatus nuclear,
        int warheads,
        int fateTokens,
        List<Deposit> deposits,
        List<PersonCard> people,
        CountryOrigin origin,
        List<Modifier> modifiers,
        SortedSet<String> tags,
        List<RollRecord> rolls) {

    public CountryCard {
        Checks.inRange("number", number, 0, Integer.MAX_VALUE);
        Objects.requireNonNull(name, "name");
        Checks.inRange("capital", capital, 0, Integer.MAX_VALUE);
        Checks.inRange("provinces", provinces, 1, Integer.MAX_VALUE);
        Checks.inRange("population_k", populationK, 0, Long.MAX_VALUE);
        Objects.requireNonNull(ideology, "ideology");
        Objects.requireNonNull(subIdeology, "subIdeology");
        Objects.requireNonNull(religion, "religion");
        worldReligions = List.copyOf(worldReligions);
        EnumMap<TechBranch, Integer> branches = new EnumMap<>(TechBranch.class);
        branches.putAll(development);
        development = Collections.unmodifiableMap(branches);
        Checks.inRange("gdp_per_capita", gdpPerCapita, 0, Integer.MAX_VALUE);
        Checks.inRange("army_share_bp", armyShareBp, 0, 10_000);
        Objects.requireNonNull(nuclear, "nuclear");
        Checks.inRange("warheads", warheads, 0, Integer.MAX_VALUE);
        Checks.inRange("fate_tokens", fateTokens, 0, Integer.MAX_VALUE);
        deposits = List.copyOf(deposits);
        people = List.copyOf(people);
        Objects.requireNonNull(origin, "origin");
        modifiers = List.copyOf(modifiers);
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        rolls = List.copyOf(rolls);
    }

    /**
     * Родовище держави.
     *
     * @param province комірка провінції
     * @param resource ресурс
     */
    public record Deposit(int province, ResourceId resource) {

        public Deposit {
            Checks.inRange("province", province, 0, Integer.MAX_VALUE);
            Objects.requireNonNull(resource, "resource");
        }
    }

    /**
     * Відома людина держави.
     *
     * @param name ім'я
     * @param kind хто вона
     * @param sex стать
     * @param traits риси
     * @param bornTurn хід народження (може бути від'ємним — до 1970 року)
     * @param alive чи жива
     */
    public record PersonCard(
            LocalizedName name, PersonKind kind, Sex sex, List<TraitId> traits, int bornTurn, boolean alive) {

        public PersonCard {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(sex, "sex");
            traits = List.copyOf(traits);
        }
    }
}
