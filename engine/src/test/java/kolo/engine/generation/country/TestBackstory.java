package kolo.engine.generation.country;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kolo.engine.content.BackstoryContent;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.BackstoryText;
import kolo.engine.content.BalanceDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.content.DoctrineDef;
import kolo.engine.content.DoctrineId;
import kolo.engine.content.GenerationBalanceDef;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.MedianRange;
import kolo.engine.content.NameContent;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameParadigmDef;
import kolo.engine.content.NameParadigmId;
import kolo.engine.content.NamePartsDef;
import kolo.engine.content.NameStyleDef;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.PersonKindDef;
import kolo.engine.content.PersonNameStyleDef;
import kolo.engine.content.PowerCorridorDef;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.StateFormDef;
import kolo.engine.content.StateFormId;
import kolo.engine.content.StreakRulesDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.SurnameFinalDef;
import kolo.engine.content.TagCondition;
import kolo.engine.content.TechBranchDef;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.content.WheelBalanceDef;
import kolo.engine.state.Development;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.TechBranch;

/** Контент для тестів колеса передісторії: невеликий набір фрагментів з умовами, сусідами й різними роками. */
final class TestBackstory {

    /** Мітки, які в справжньому контенті дали б колеса генерації. */
    static final Map<String, String> GENERATION_TAGS = Map.of("junta", "Хунта.", "pacifist", "Пацифізм.");

    /** Без умов, з широкими роками: їх завжди досить на {@link #COUNT} фрагментів. */
    static final BackstoryFragmentDef FLOOD = fragment("flood", 100, 20, 1900, 1969, List.of("flood"));

    static final BackstoryFragmentDef FAMINE = fragment("famine", 100, 10, 1900, 1969, List.of("famine"));
    static final BackstoryFragmentDef GOLDEN_AGE = fragment("golden_age", 100, 90, 1900, 1969, List.of());
    static final BackstoryFragmentDef CIVIL_WAR = fragment("civil_war", 100, 15, 1946, 1966, List.of("civil_war"));
    /** Лише після громадянської війни. */
    static final BackstoryFragmentDef RECONCILIATION = new BackstoryFragmentDef(
            new BackstoryFragmentId("reconciliation"),
            300,
            60,
            1950,
            1969,
            new TagCondition(List.of("civil_war"), List.of(), List.of()),
            new TreeMap<>(),
            false,
            List.of("reconciliation"),
            0,
            List.of(),
            new BackstoryText("text", "Примирення {year} року в {country.locative}."));
    /** Мітка {@code pacifist} зводить вагу до нуля. */
    static final BackstoryFragmentDef ARMS_RACE = new BackstoryFragmentDef(
            new BackstoryFragmentId("arms_race"),
            100,
            50,
            1900,
            1969,
            TagCondition.NONE,
            new TreeMap<>(Map.of("pacifist", -100, "junta", 5000)),
            false,
            List.of("arms_race"),
            0,
            List.of(),
            new BackstoryText("text", "Гонка озброєнь {year} року в {country.locative}."));

    static final BackstoryFragmentDef OLD_FEUD = neighborFragment("old_feud", 1920, 1960);
    static final BackstoryFragmentDef BORDER_WAR = neighborFragment("border_war", 1930, 1969);

    static final CountRange COUNT = new CountRange(2, 4);

    static final List<BackstoryFragmentDef> FRAGMENTS =
            List.of(FLOOD, FAMINE, GOLDEN_AGE, CIVIL_WAR, RECONCILIATION, ARMS_RACE, OLD_FEUD, BORDER_WAR);

    static final ContentPack PACK = pack(FRAGMENTS, COUNT);

    static final LocalizedName COUNTRY = name("Велор");
    static final LocalizedName NEIGHBOR = name("Тормар");

    private TestBackstory() {}

    static BackstoryFragmentDef fragment(String id, int weight, int quality, int from, int to, List<String> adds) {
        return new BackstoryFragmentDef(
                new BackstoryFragmentId(id),
                weight,
                quality,
                from,
                to,
                TagCondition.NONE,
                new TreeMap<>(),
                false,
                adds,
                0,
                List.of(),
                new BackstoryText("text", "Подія " + id + " {year} року в {country.locative}."));
    }

    private static BackstoryFragmentDef neighborFragment(String id, int from, int to) {
        return new BackstoryFragmentDef(
                new BackstoryFragmentId(id),
                100,
                30,
                from,
                to,
                TagCondition.NONE,
                new TreeMap<>(),
                true,
                List.of(id),
                0,
                List.of(),
                new BackstoryText("text", "Подія " + id + " {year} року з {neighbor.instrumental}."));
    }

    /** Мінімальний валідний пакет з цими фрагментами й кількістю фрагментів передісторії. */
    static ContentPack pack(List<BackstoryFragmentDef> fragments, CountRange count) {
        return pack(
                List.of(new IdeologyDef(
                        new IdeologyId("democracy"),
                        "Демократія",
                        100,
                        List.of(),
                        List.of("democratic"),
                        List.of(new SubIdeologyDef(
                                new SubIdeologyId("liberal_democracy"), "Ліберальна", 100, List.of(), List.of())))),
                fragments,
                count);
    }

    /** Мінімальний валідний пакет з цими ідеологіями; форма державності доступна кожній. */
    static ContentPack pack(List<IdeologyDef> ideologies, List<BackstoryFragmentDef> fragments, CountRange count) {
        return pack(
                ideologies,
                fragments,
                count,
                Development.levels().stream()
                        .map(level -> new DevelopmentLevelDef(level, "Рівень", "Опис", 100, 50, List.of()))
                        .toList());
    }

    /** Мінімальний валідний пакет з цими ідеологіями й рівнями розвиненості. */
    static ContentPack pack(
            List<IdeologyDef> ideologies,
            List<BackstoryFragmentDef> fragments,
            CountRange count,
            List<DevelopmentLevelDef> levels) {
        NameParadigmDef masc = new NameParadigmDef(
                new NameParadigmId("masc_hard"),
                GrammaticalGender.MASCULINE,
                List.of("", "у", "у", "", "ом", "і", "е"));
        NameParadigmDef fem = new NameParadigmDef(
                new NameParadigmId("fem_hard"),
                GrammaticalGender.FEMININE,
                List.of("а", "и", "і", "у", "ою", "і", "о"));
        NameStyleDef style = new NameStyleDef(
                new NameStyleId("northern"),
                "Північний",
                List.of("вел"),
                List.of(),
                0,
                List.of(new NameFinalDef("ор", masc.id())));
        PersonNameStyleDef people = new PersonNameStyleDef(
                style.id(),
                new NamePartsDef(List.of("вел"), List.of(), 0),
                List.of(new NameFinalDef("ор", masc.id())),
                List.of(new NameFinalDef("ен", fem.id())),
                new NamePartsDef(List.of("торв"), List.of(), 0),
                List.of(new SurnameFinalDef("ер", masc.id(), fem.id())));
        StateFormDef republic = new StateFormDef(
                new StateFormId("republic"),
                GrammaticalGender.FEMININE,
                List.of(
                        "Республіка {root}",
                        "Республіки {root}",
                        "Республіці {root}",
                        "Республіку {root}",
                        "Республікою {root}",
                        "Республіці {root}",
                        "Республіко {root}"),
                ideologies.stream().map(IdeologyDef::id).toList(),
                List.of());

        return new ContentPack(
                "0".repeat(64),
                ideologies,
                List.of(new DoctrineDef(new DoctrineId("armored"), "Бронетанкова", List.of(), List.of())),
                List.of(new ResourceDef(new ResourceId("iron"), "Залізо", List.of())),
                Arrays.stream(TechBranch.values())
                        .map(branch -> new TechBranchDef(branch, "Галузь"))
                        .toList(),
                levels,
                Arrays.stream(NuclearStatus.values())
                        .map(status -> new NuclearStatusDef(status, "Статус", List.of()))
                        .toList(),
                Arrays.stream(PersonKind.values())
                        .map(kind -> new PersonKindDef(kind, "Тип", "Опис", List.of()))
                        .toList(),
                List.of(new TraitDef(new TraitId("loyal"), "Відданий", List.of(), List.of(), List.of(), List.of())),
                new NameContent(List.of(masc, fem), List.of(style), List.of(republic), List.of(people)),
                new BackstoryContent(GENERATION_TAGS, fragments),
                BalanceDef.of(
                        new WheelBalanceDef(50, new TreeMap<>(), List.of(10)),
                        new StreakRulesDef(85, 15, 3),
                        Arrays.stream(PowerCorridor.values())
                                .map(corridor -> new PowerCorridorDef(
                                        corridor, new MedianRange(50, 200), new MedianRange(50, 200)))
                                .toList(),
                        new GenerationBalanceDef(count, new CountRange(1, 3))));
    }

    /** Чоловічий рід, тверда група: «Велор, Велору, …» — і для короткої, і для повної назви. */
    private static LocalizedName name(String nominative) {
        List<String> endings = List.of("", "у", "у", "", "ом", "і", "е");
        List<String> forms = endings.stream().map(ending -> nominative + ending).toList();
        NounPhrase phrase = new NounPhrase(GrammaticalGender.MASCULINE, forms);
        return new LocalizedName(phrase, phrase);
    }
}
