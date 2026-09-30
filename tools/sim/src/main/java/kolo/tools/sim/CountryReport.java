package kolo.tools.sim;

import static kolo.tools.sim.SimText.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SortedSet;
import java.util.stream.Collectors;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MedianRange;
import kolo.engine.generation.country.BackstoryEntry;
import kolo.engine.generation.country.PowerBudget;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.country.StartDevelopment;
import kolo.engine.generation.country.StartGeography;
import kolo.engine.generation.country.StartName;
import kolo.engine.generation.country.StartPerson;
import kolo.engine.generation.country.StreakBonus;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.generation.map.PlacementMap;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.religion.StartHolyCenters;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.CountryId;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.Stat;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.WheelKind;

/**
 * Картка згенерованої держави для консолі: назви з контенту, числа — у звичних одиницях. Лише показує результат
 * рушія, нічого не рахує.
 */
final class CountryReport {

    /** Рік ходу 0 (1 хід = 1 рік). */
    private static final int START_YEAR = 1970;

    private static final long MILLION = 1_000_000;

    private final ContentPack content;
    private final WorldMap map;
    private final StartReligions religions;
    private final StartHolyCenters holyCenters;
    private final List<StartCountry> countries;
    private final int number;
    private final StartCountry country;
    private final List<String> lines = new ArrayList<>();

    CountryReport(CountryCommand.Result result) {
        this.content = result.content();
        this.map = result.map();
        this.religions = result.religions();
        this.holyCenters = result.world().holyCenters();
        this.countries = result.countries();
        this.number = result.number();
        this.country = result.country();
    }

    List<String> lines(long seed, boolean rolls) {
        lines.clear();
        WorldSize size = map.size();
        add(
                "country.header",
                seed,
                size.players(),
                size.npc(),
                content.map().template(size.template()).orElseThrow().name(),
                content.hash().substring(0, 12));
        add("country.number", number, countries.size() - 1);
        lines.add("");
        name();
        territory();
        geography();
        add(
                "country.population",
                thousands(country.population().populationK()),
                content.map()
                        .population()
                        .level(country.population().level())
                        .orElseThrow()
                        .name());
        neighbors();
        add(
                "country.regime",
                country.regime().ideology().name(),
                country.regime().subIdeology().name());
        religion();
        development();
        add(
                "country.gdp",
                country.gdp().perCapita(),
                content.gdpLevel(country.gdp().level()).orElseThrow().name(),
                country.totalGdp() / MILLION);
        add(
                "country.hdi",
                country.hdi().hdi(),
                content.hdiLevel(country.hdi().level()).orElseThrow().name());
        add(
                "country.army_size",
                country.armyStrength(),
                percent(country.armySize().shareBp()),
                content.armySize(country.armySize().size()).orElseThrow().name());
        add(
                "country.army_training",
                content.trainingLevel(country.armyTraining().level()).name(),
                signed(country.armyTraining().combatModifier()));
        resources();
        nuclear();
        add("country.fate_tokens", country.fateTokens());
        streaks();
        power();
        backstory();
        people();
        lines.add("");
        add("country.tags", country.tags().isEmpty() ? text("country.none") : String.join(", ", country.tags()));
        modifiers();
        worldReligions();
        if (rolls) {
            rolls();
        }
        return List.copyOf(lines);
    }

    private void name() {
        StartName name = country.name();
        LocalizedName chosen = name.name();
        add("country.name", chosen.fullName().nominative(), chosen.shortName().nominative());
        String candidates = name.candidates().stream()
                .map(candidate -> candidate.name().fullName().nominative())
                .collect(Collectors.joining(", "));
        add("country.name_candidates", candidates);
    }

    private void territory() {
        PlacedCountry territory = country.territory();
        add(
                "country.territory",
                territory.continent(),
                content.map().placement().area(territory.area()).orElseThrow().name(),
                territory.provinces());
    }

    private void geography() {
        StartGeography geography = country.geography();
        String coast = content.map().geography().coast().stream()
                .filter(level -> level.id().equals(geography.coast()))
                .findFirst()
                .orElseThrow()
                .name();
        add(
                "country.geography",
                coast,
                geography.coastalPct(),
                text("terrain." + geography.dominant().key()),
                geography.fertility());
    }

    private void neighbors() {
        SortedSet<Integer> neighbors = map.neighbors(number);
        if (neighbors.isEmpty()) {
            add("country.neighbors", text("country.none"));
            return;
        }
        String names = neighbors.stream().map(this::countryName).collect(Collectors.joining(", "));
        add("country.neighbors", names);
    }

    private void resources() {
        if (country.resources().deposits().isEmpty()) {
            add("country.resources", text("country.none"));
            return;
        }
        String deposits = country.resources().deposits().stream()
                .map(deposit -> text(
                        "country.deposit",
                        content.resource(deposit.resource()).orElseThrow().name(),
                        deposit.cell()))
                .collect(Collectors.joining(", "));
        add("country.resources", deposits);
    }

    private void religion() {
        if (country.religion().secular()) {
            add(
                    "country.religion_secular",
                    content.religions().stateReligion().secular().name());
        } else {
            StartReligion religion =
                    religions.religions().get(country.religion().religion().getAsInt());
            add("country.religion", religion.name().nominative());
        }
    }

    private void development() {
        StartDevelopment development = country.development();
        List<String> parts = new ArrayList<>();
        for (TechBranch branch : TechBranch.values()) {
            int level = development.level(branch);
            parts.add(text(
                    "country.development_branch",
                    content.techBranch(branch).name(),
                    signed(level),
                    content.developmentLevel(level).name()));
        }
        add("country.development", String.join("; ", parts));
    }

    private void nuclear() {
        String status = content.nuclearStatus(country.nuclear().status()).name();
        if (country.nuclear().status() == NuclearStatus.ARSENAL) {
            add("country.nuclear_arsenal", status, country.nuclear().warheads());
        } else {
            add("country.nuclear", status);
        }
    }

    private void streaks() {
        if (country.streaks().isEmpty()) {
            add("country.streaks", text("country.none"));
            return;
        }
        String streaks = country.streaks().stream().map(this::streak).collect(Collectors.joining("; "));
        add("country.streaks", streaks);
    }

    private String streak(StreakBonus bonus) {
        return text(
                "country.streak",
                content.streaks().wheel(bonus.streak()).name(),
                bonus.reward().name());
    }

    private void backstory() {
        lines.add("");
        add("country.backstory");
        if (country.backstory().entries().isEmpty()) {
            add("country.list_empty");
        }
        LocalizedName name = country.name().name();
        Optional<LocalizedName> neighbor = country.backstory()
                .neighbor()
                .map(id -> countries.get(numberOf(id)).name().name());
        for (BackstoryEntry entry : country.backstory().entries()) {
            add("country.backstory_entry", entry.year(), entry.text(name, neighbor));
        }
    }

    private void people() {
        lines.add("");
        add("country.people");
        for (StartPerson person : country.people().people()) {
            String traits = person.traits().stream()
                    .map(trait -> content.trait(trait).orElseThrow().name())
                    .collect(Collectors.joining(", "));
            add(
                    "country.person",
                    person.name().fullName().nominative(),
                    content.personKind(person.kind()).name(),
                    text("sex." + person.sex().key()),
                    person.age(),
                    traits);
        }
    }

    private void modifiers() {
        lines.add("");
        add("country.modifiers");
        if (country.modifiers().isEmpty()) {
            add("country.list_empty");
        }
        for (Modifier modifier : country.modifiers()) {
            String term = modifier.expiresAtTurn() == null
                    ? text("country.modifier_permanent")
                    : text("country.modifier_until", START_YEAR + modifier.expiresAtTurn());
            add("country.modifier", target(modifier.target()), signed(modifier.value()), term, modifier.id());
        }
    }

    private void worldReligions() {
        lines.add("");
        add("country.world_religions");
        for (int i = 0; i < religions.religions().size(); i++) {
            StartReligion religion = religions.religions().get(i);
            int cell = holyCenters.cell(i);
            int owner = map.placement().country(cell);
            add(
                    "country.world_religion",
                    i,
                    religion.name().nominative(),
                    content.religions()
                            .archetype(religion.archetype())
                            .orElseThrow()
                            .name(),
                    cell,
                    owner == PlacementMap.NONE ? text("country.unclaimed") : countryName(owner));
        }
    }

    /** Коротка назва держави з номером: «Велмар (3)». */
    private String countryName(int number) {
        return text(
                "country.neighbor",
                number,
                countries.get(number).name().name().shortName().nominative());
    }

    private void power() {
        PowerBudget power = country.power();
        MedianRange range = power.range(content.balance());
        add(
                "country.power",
                power.strengthPct(),
                text("corridor." + power.corridor().key()),
                text(power.npc() ? "country.power_npc" : "country.power_player"),
                range.minPct(),
                range.maxPct());
        String steps = power.steps().stream()
                .map(step -> text(
                        "country.power_step",
                        step.component().key(),
                        step.quality(),
                        step.strengthPct(),
                        signed(step.advantage())))
                .collect(Collectors.joining("; "));
        add("country.power_steps", steps);
    }

    private void rolls() {
        lines.add("");
        add("country.rolls");
        for (RollRecord roll : country.rolls()) {
            add("country.roll", roll.kind().id(), roll.resultSectorId(), roll.roll(), signed(roll.advantage()));
            if (!roll.modifiers().isEmpty()) {
                String why =
                        roll.modifiers().stream().map(CountryReport::applied).collect(Collectors.joining(", "));
                add("country.roll_modifiers", why);
            }
            String sectors = roll.sectors().stream().map(CountryReport::sector).collect(Collectors.joining(", "));
            add("country.roll_sectors", sectors);
        }
    }

    private static String applied(AppliedModifier modifier) {
        return modifier.sourceId() + " " + signed(modifier.value());
    }

    private static String sector(RolledSector sector) {
        return sector.id() + " " + percent(sector.weightBp());
    }

    private static String target(ModifierTarget target) {
        return switch (target) {
            case ModifierTarget.StatTarget(Stat stat) -> "stat:" + stat.name().toLowerCase(Locale.ROOT);
            case ModifierTarget.WheelTarget(WheelKind kind) -> "wheel:" + kind.id();
        };
    }

    private void add(String key, Object... args) {
        lines.add(text(key, args));
    }

    /** Номер держави з її ідентифікатора {@code cty_<номер>}. */
    static int numberOf(CountryId id) {
        return Integer.parseInt(id.value().substring(CountryId.PREFIX.length()));
    }

    /** Тисячі людей для читання: до мільйона — «850 тис.», далі — мільйони з однією цифрою після коми вниз. */
    static String thousands(long thousands) {
        if (thousands < 1_000) {
            return text("country.thousands", thousands);
        }
        long tenths = thousands / 100;
        String value = tenths % 10 == 0 ? String.valueOf(tenths / 10) : tenths / 10 + "," + tenths % 10;
        return text("country.millions", value);
    }

    /** Базисні пункти як відсотки з українською комою без зайвих нулів: 150 → «1,5%», 800 → «8%». */
    static String percent(int bp) {
        String sign = bp < 0 ? "−" : "";
        int abs = Math.abs(bp);
        int whole = abs / 100;
        int fraction = abs % 100;
        if (fraction == 0) {
            return sign + whole + "%";
        }
        String digits =
                fraction % 10 == 0 ? String.valueOf(fraction / 10) : String.format(Locale.ROOT, "%02d", fraction);
        return sign + whole + "," + digits + "%";
    }

    /** Число зі знаком, мінус — типографський: 10 → «+10», 0 → «0», −3 → «−3». */
    static String signed(int value) {
        if (value > 0) {
            return "+" + value;
        }
        return value < 0 ? "−" + Math.abs((long) value) : "0";
    }
}
