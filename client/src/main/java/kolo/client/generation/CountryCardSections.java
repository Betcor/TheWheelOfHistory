package kolo.client.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kolo.client.i18n.Texts;
import kolo.engine.content.ContentPack;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.content.TraitDef;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.TechBranch;
import kolo.engine.view.CountryCard;

/**
 * Картка держави гравця (GD §4.12) як розділи з рядками «назва — значення»: усе, що дала генерація. Без JavaFX — екран
 * лише розкладає рядки.
 */
public final class CountryCardSections {

    private CountryCardSections() {}

    /**
     * Розділ картки.
     *
     * @param title назва розділу
     * @param lines рядки; порожній розділ на картці не показується
     */
    public record Section(String title, List<Line> lines) {

        public Section {
            Objects.requireNonNull(title, "title");
            lines = List.copyOf(lines);
        }
    }

    /**
     * Рядок картки.
     *
     * @param label назва; порожня — рядок без назви (текст передісторії)
     */
    public record Line(String label, String value) {

        public Line {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
        }
    }

    /** @param turn поточний хід: вік відомих людей — на цей рік */
    public static List<Section> of(GenerationLabels labels, Texts texts, int turn) {
        CountryCard card = labels.card();
        ContentPack content = labels.content();
        List<Section> sections = new ArrayList<>();

        List<Line> state = new ArrayList<>();
        state.add(line(texts, "name", card.name().fullName().nominative()));
        state.add(line(
                texts,
                "territory",
                texts.text(
                        "card.territory.value",
                        card.provinces(),
                        content.map()
                                .placement()
                                .area(card.origin().area())
                                .map(def -> def.name())
                                .orElse(card.origin().area().value()))));
        state.add(line(texts, "capital", texts.text("card.capital.value", card.capital())));
        state.add(line(texts, "population", population(texts, card.populationK())));
        state.add(line(
                texts,
                "regime",
                texts.text(
                        "card.regime.value",
                        content.ideology(card.ideology())
                                .map(def -> def.name())
                                .orElse(card.ideology().value()),
                        content.subIdeology(card.subIdeology())
                                .map(def -> def.name())
                                .orElse(card.subIdeology().value()))));
        state.add(line(
                texts,
                "religion",
                card.religion()
                        .map(religion -> religion.name().nominative())
                        .orElseGet(() ->
                                content.religions().stateReligion().secular().name())));
        sections.add(new Section(texts.text("card.section.state"), state));

        List<Line> development = new ArrayList<>();
        for (Map.Entry<TechBranch, Integer> branch : card.development().entrySet()) {
            development.add(
                    new Line(content.techBranch(branch.getKey()).name(), labels.developmentText(branch.getValue())));
        }
        development.add(line(texts, "gdp", texts.text("card.gdp.value", card.gdpPerCapita(), gdpMillions(card))));
        development.add(line(texts, "hdi", Integer.toString(card.hdi())));
        sections.add(new Section(texts.text("card.section.development"), development));

        List<Line> army = new ArrayList<>();
        army.add(line(texts, "army", texts.text("card.army.value", soldiers(card), percent(card.armyShareBp()))));
        army.add(line(
                texts,
                "training",
                content.trainingLevels().containsKey(card.training())
                        ? content.trainingLevel(card.training()).name()
                        : Integer.toString(card.training())));
        sections.add(new Section(texts.text("card.section.army"), army));

        List<Line> resources = new ArrayList<>();
        for (CountryCard.Deposit deposit : card.deposits()) {
            resources.add(new Line(
                    content.resource(deposit.resource())
                            .map(ResourceDef::name)
                            .orElse(deposit.resource().value()),
                    texts.text("card.deposit.value", deposit.province())));
        }
        String nuclear = content.nuclearStatus(card.nuclear()).name();
        resources.add(line(
                texts,
                "nuclear",
                card.warheads() > 0 ? texts.text("card.nuclear.warheads", nuclear, card.warheads()) : nuclear));
        sections.add(new Section(texts.text("card.section.resources"), resources));

        List<Line> history = new ArrayList<>();
        for (CountryOrigin.Backstory entry : card.origin().backstory()) {
            history.add(new Line(Integer.toString(entry.year()), labels.backstory(entry)));
        }
        for (CountryOrigin.Streak streak : card.origin().streaks()) {
            history.add(new Line(
                    content.streaks().wheel(streak.kind()).name(),
                    content.streaks()
                            .wheel(streak.kind())
                            .reward(streak.reward())
                            .map(StreakRewardDef::name)
                            .orElse(streak.reward().value())));
        }
        if (card.fateTokens() > 0) {
            history.add(line(texts, "fate_tokens", Integer.toString(card.fateTokens())));
        }
        sections.add(new Section(texts.text("card.section.history"), history));

        List<Line> people = new ArrayList<>();
        for (CountryCard.PersonCard person : card.people()) {
            List<String> traits = person.traits().stream()
                    .map(trait -> content.trait(trait).map(TraitDef::name).orElse(trait.value()))
                    .toList();
            String about = texts.text(
                    "card.person.value",
                    content.personKind(person.kind()).name(),
                    age(person.bornTurn(), turn),
                    traits.isEmpty() ? texts.text("card.person.no_traits") : String.join(", ", traits));
            people.add(new Line(person.name().fullName().nominative(), about));
        }
        sections.add(new Section(texts.text("card.section.people"), people));
        return sections;
    }

    /** Вік на хід {@code turn}, повних років (1 хід = 1 рік). */
    static int age(int bornTurn, int turn) {
        return Math.subtractExact(turn, bornTurn);
    }

    /** Населення: «850 тис.», «6 млн», «12,4 млн». */
    static String population(Texts texts, long populationK) {
        if (populationK < 1000) {
            return texts.text("card.population.thousands", populationK);
        }
        long millions = populationK / 1000;
        long tenths = populationK % 1000 / 100;
        return texts.text("card.population.millions", tenths == 0 ? millions : millions + "," + tenths);
    }

    /** Частка з bp: «0,4%», «12%». */
    static String percent(int bp) {
        int whole = bp / 100;
        int tenths = bp % 100 / 10;
        return (tenths == 0 ? Integer.toString(whole) : whole + "," + tenths) + "%";
    }

    /** Загальний ВВП, млн $ 1970 року. */
    private static long gdpMillions(CountryCard card) {
        return Math.multiplyExact((long) card.gdpPerCapita(), card.populationK()) / 1000;
    }

    /** Чисельність армії, осіб. */
    private static long soldiers(CountryCard card) {
        return Math.multiplyExact(card.populationK(), (long) card.armyShareBp()) * 1000 / 10_000;
    }

    private static Line line(Texts texts, String key, String value) {
        return new Line(texts.text("card." + key), value);
    }
}
