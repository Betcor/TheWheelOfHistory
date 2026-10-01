package kolo.client.generation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import kolo.client.i18n.Texts;
import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.HdiLevelDef;
import kolo.engine.content.HdiLevelId;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.PopulationLevelDef;
import kolo.engine.content.PopulationLevelId;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.content.StreakRewardId;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.TechBranch;
import kolo.engine.view.CountryCard;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.WheelKind;

/**
 * Підписи коліс генерації й їхніх секторів: назви з контенту гри (того самого, що в сервера), числа й службові назви — з
 * текстів клієнта. Сектор, якого контент не знає, підписано його id: гравець бачить хоч щось, а не помилку.
 */
public final class GenerationLabels {

    /** Найдовший підпис фрагмента передісторії на колесі: далі — «…». */
    static final int MAX_FRAGMENT = 60;

    private static final String GENERATION = "generation_";
    private static final String DEVELOPMENT = "generation_development_";

    private final ContentPack content;
    private final Texts texts;
    private final CountryCard card;
    private final IntFunction<Optional<LocalizedName>> countries;

    /**
     * @param countries назва держави за номером на карті — для сусіда передісторії
     */
    public GenerationLabels(
            ContentPack content, Texts texts, CountryCard card, IntFunction<Optional<LocalizedName>> countries) {
        this.content = Objects.requireNonNull(content, "content");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.card = Objects.requireNonNull(card, "card");
        this.countries = Objects.requireNonNull(countries, "countries");
    }

    public ContentPack content() {
        return content;
    }

    public CountryCard card() {
        return card;
    }

    /** Чи знає клієнт, як назвати колесо цього типу (інакше {@link #wheel} — id). */
    public boolean knowsWheel(WheelKind kind) {
        return streak(kind).isPresent() || development(kind).isPresent() || texts.has("wheel." + kind.id());
    }

    /** Назва колеса, напр. «Площа», «Розвиненість: Економіка», «Стрік: Золота доба». */
    public String wheel(WheelKind kind) {
        Optional<StreakKind> streak = streak(kind);
        if (streak.isPresent()) {
            return texts.text(
                    "wheel.streak", content.streaks().wheel(streak.get()).name());
        }
        Optional<TechBranch> branch = development(kind);
        if (branch.isPresent()) {
            return texts.text(
                    "wheel.development", content.techBranch(branch.get()).name());
        }
        String key = "wheel." + kind.id();
        return texts.has(key) ? texts.text(key) : kind.id();
    }

    /** Сектор, що випав. */
    public String result(RollRecord roll) {
        return sector(roll, roll.resultSectorId());
    }

    /** Підпис сектора колеса; {@code id}, якщо такого сектора клієнт не знає. */
    public String sector(RollRecord roll, String id) {
        return known(roll, id).orElse(id);
    }

    /** Чи знає клієнт, як підписати сектор (інакше {@link #sector} — id). */
    public boolean knowsSector(RollRecord roll, String id) {
        return known(roll, id).isPresent();
    }

    private Optional<String> known(RollRecord roll, String id) {
        WheelKind kind = roll.kind();
        Optional<StreakKind> streak = streak(kind);
        if (streak.isPresent()) {
            return content.streaks()
                    .wheel(streak.get())
                    .reward(new StreakRewardId(id))
                    .map(StreakRewardDef::name);
        }
        if (development(kind).isPresent()) {
            return developmentLevel(id).map(level -> developmentText(level));
        }
        return switch (kind.id()) {
            case "generation_continent" ->
                number(id, "continent_").map(n -> texts.text("wheel.sector.continent", n + 1));
            case "generation_area" ->
                content.map().placement().area(new AreaLevelId(id)).map(AreaLevelDef::name);
            case "generation_population" ->
                content.map().population().level(new PopulationLevelId(id)).map(PopulationLevelDef::name);
            case "generation_ideology" -> content.ideology(new IdeologyId(id)).map(IdeologyDef::name);
            case "generation_sub_ideology" ->
                content.subIdeology(new SubIdeologyId(id)).map(SubIdeologyDef::name);
            case "generation_state_religion" -> religion(id);
            case "generation_gdp" -> content.gdpLevel(new GdpLevelId(id)).map(GdpLevelDef::name);
            case "generation_hdi" -> content.hdiLevel(new HdiLevelId(id)).map(HdiLevelDef::name);
            case "generation_army_size" -> content.armySize(new ArmySizeId(id)).map(ArmySizeDef::name);
            case "generation_army_training" ->
                number(id, "level_")
                        .filter(level -> content.trainingLevels().containsKey(level))
                        .map(level -> content.trainingLevel(level))
                        .map(TrainingLevelDef::name);
            case "generation_resource" -> content.resource(new ResourceId(id)).map(ResourceDef::name);
            case "generation_nuclear" -> nuclear(id);
            case "generation_name" -> name(roll, id);
            case "generation_backstory" -> fragment(id);
            case "generation_person_kind" -> personKind(id);
            case "generation_person_trait" -> content.trait(new TraitId(id)).map(TraitDef::name);
            case "generation_warheads" -> number(id, "warheads_").map(n -> texts.text("wheel.sector.warheads", n));
            case "generation_resource_count" -> count(id, "resources_");
            case "generation_backstory_count" -> count(id, "fragments_");
            case "generation_people_count" -> count(id, "people_");
            case "generation_person_trait_count" -> count(id, "traits_");
            default -> Optional.empty();
        };
    }

    /** Рівень розвиненості зі знаком і назвою, напр. «+2 · Лідер». */
    public String developmentText(int level) {
        DevelopmentLevelDef def = content.developmentLevels().get(level);
        String sign = level > 0 ? "+" + level : Integer.toString(level);
        return def == null ? sign : texts.text("wheel.sector.development", sign, def.name());
    }

    /** Текст фрагмента передісторії про цю державу. */
    public String backstory(CountryOrigin.Backstory entry) {
        return content.backstory()
                .fragment(entry.fragment())
                .map(fragment -> render(fragment, entry.year()))
                .orElse(entry.fragment().value());
    }

    /** Сусід передісторії за номером на карті, якщо він є. */
    public Optional<LocalizedName> neighbor() {
        return card.origin().backstoryNeighbor().flatMap(id -> countries.apply(Math.toIntExact(id.number())));
    }

    private String render(BackstoryFragmentDef fragment, int year) {
        Optional<LocalizedName> neighbor = neighbor();
        if (fragment.text().usesNeighbor() && neighbor.isEmpty()) {
            // Фрагмент, що не випав, міг би згадати сусіда, якого в держави немає.
            neighbor = Optional.of(placeholderNeighbor());
        }
        return fragment.text().render(card.name(), neighbor, year);
    }

    private LocalizedName placeholderNeighbor() {
        NounPhrase word = new NounPhrase(
                GrammaticalGender.MASCULINE,
                List.of(texts.text("wheel.neighbor").split(",")));
        return new LocalizedName(word, word);
    }

    private Optional<String> fragment(String id) {
        Optional<BackstoryFragmentDef> fragment = content.backstory().fragment(new BackstoryFragmentId(id));
        if (fragment.isEmpty()) {
            return Optional.empty();
        }
        // Фрагмент, що випав, — з його роком; інші — з першим можливим роком.
        int year = card.origin().backstory().stream()
                .filter(entry -> entry.fragment().value().equals(id))
                .mapToInt(CountryOrigin.Backstory::year)
                .findFirst()
                .orElse(fragment.get().yearFrom());
        return Optional.of(shorten(render(fragment.get(), year)));
    }

    static String shorten(String text) {
        return text.length() <= MAX_FRAGMENT
                ? text
                : text.substring(0, MAX_FRAGMENT - 1).stripTrailing() + "…";
    }

    private Optional<String> religion(String id) {
        if (id.equals("secular")) {
            return Optional.of(content.religions().stateReligion().secular().name());
        }
        return number(id, "religion_")
                .filter(n -> n < card.worldReligions().size())
                .map(n -> card.worldReligions().get(n).nominative());
    }

    private Optional<String> name(RollRecord roll, String id) {
        // Кандидатів назви сервер не зберігає — відома лише та, що випала.
        if (id.equals(roll.resultSectorId())) {
            return Optional.of(card.name().fullName().nominative());
        }
        return number(id, "name_").map(n -> texts.text("wheel.sector.name_variant", n));
    }

    private Optional<String> nuclear(String id) {
        for (NuclearStatus status : NuclearStatus.values()) {
            if (status.key().equals(id)) {
                return Optional.of(content.nuclearStatus(status).name());
            }
        }
        return Optional.empty();
    }

    private Optional<String> personKind(String id) {
        for (PersonKind kind : PersonKind.values()) {
            if (kind.key().equals(id)) {
                return Optional.of(content.personKind(kind).name());
            }
        }
        return Optional.empty();
    }

    private Optional<String> count(String id, String prefix) {
        return number(id, prefix).map(n -> texts.text("wheel.sector.count", n));
    }

    private static Optional<Integer> developmentLevel(String id) {
        if (id.equals("level_0")) {
            return Optional.of(0);
        }
        return number(id, "level_plus_").or(() -> number(id, "level_minus_").map(n -> -n));
    }

    /** Ціле число після префікса, напр. {@code continent_3} → 3. */
    static Optional<Integer> number(String id, String prefix) {
        if (!id.startsWith(prefix)) {
            return Optional.empty();
        }
        String digits = id.substring(prefix.length());
        if (digits.isEmpty() || digits.length() > 6 || !digits.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(digits));
    }

    private static Optional<StreakKind> streak(WheelKind kind) {
        for (StreakKind streak : StreakKind.values()) {
            if (kind.id().equals(GENERATION + streak.key())) {
                return Optional.of(streak);
            }
        }
        return Optional.empty();
    }

    private static Optional<TechBranch> development(WheelKind kind) {
        for (TechBranch branch : TechBranch.values()) {
            if (kind.id().equals(DEVELOPMENT + branch.key())) {
                return Optional.of(branch);
            }
        }
        return Optional.empty();
    }
}
