package kolo.tools.sim;

import static kolo.tools.sim.SimText.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.country.BackstoryEntry;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.country.StartDevelopment;
import kolo.engine.generation.country.StartName;
import kolo.engine.generation.country.StartPerson;
import kolo.engine.generation.country.StreakBonus;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierTarget;
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

    private final ContentPack content;
    private final StartReligions religions;
    private final StartCountry country;
    private final List<String> lines = new ArrayList<>();

    CountryReport(ContentPack content, StartReligions religions, StartCountry country) {
        this.content = content;
        this.religions = religions;
        this.country = country;
    }

    List<String> lines(long seed, int countries, boolean rolls) {
        lines.clear();
        add("country.header", seed, countries, content.hash().substring(0, 12));
        lines.add("");
        name();
        add(
                "country.regime",
                country.regime().ideology().name(),
                country.regime().subIdeology().name());
        religion();
        development();
        add(
                "country.gdp",
                country.gdp().perCapita(),
                content.gdpLevel(country.gdp().level()).orElseThrow().name());
        add(
                "country.hdi",
                country.hdi().hdi(),
                content.hdiLevel(country.hdi().level()).orElseThrow().name());
        add(
                "country.army_size",
                percent(country.armySize().shareBp()),
                content.armySize(country.armySize().size()).orElseThrow().name());
        add(
                "country.army_training",
                content.trainingLevel(country.armyTraining().level()).name(),
                signed(country.armyTraining().combatModifier()));
        nuclear();
        add("country.fate_tokens", country.fateTokens());
        streaks();
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
        for (BackstoryEntry entry : country.backstory().entries()) {
            // Сусідів CLI не задає, тож фрагментів із сусідом передісторія не обирає.
            add("country.backstory_entry", entry.year(), entry.text(name, Optional.empty()));
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
            add(
                    "country.world_religion",
                    i,
                    religion.name().nominative(),
                    content.religions()
                            .archetype(religion.archetype())
                            .orElseThrow()
                            .name());
        }
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
