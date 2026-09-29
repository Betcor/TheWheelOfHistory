package kolo.engine.generation.name;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ArmySizeId;
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
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.GenerationBalanceDef;
import kolo.engine.content.HdiLevelDef;
import kolo.engine.content.HdiLevelId;
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
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.content.WheelBalanceDef;
import kolo.engine.state.Development;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.TechBranch;
import kolo.engine.state.Training;
import kolo.engine.wheel.OutcomeTier;

/**
 * Контент для тестів генераторів назв та імен: дві ідеології, два стилі, три форми державності. Відкритий, бо ним
 * користуються й тести колеса назви в {@code generation.country}.
 */
public final class TestNames {

    public static final NameParadigmDef MASC_HARD = new NameParadigmDef(
            new NameParadigmId("masc_hard"), GrammaticalGender.MASCULINE, List.of("", "у", "у", "", "ом", "і", "е"));
    public static final NameParadigmDef FEM_IYA = new NameParadigmDef(
            new NameParadigmId("fem_iya"), GrammaticalGender.FEMININE, List.of("я", "ї", "ї", "ю", "єю", "ї", "є"));

    static final NameParadigmDef PERSON_MASC = new NameParadigmDef(
            new NameParadigmId("person_masc_hard"),
            GrammaticalGender.MASCULINE,
            List.of("", "а", "ові", "а", "ом", "ові", "е"));
    static final NameParadigmDef FEM_HARD = new NameParadigmDef(
            new NameParadigmId("fem_hard"), GrammaticalGender.FEMININE, List.of("а", "и", "і", "у", "ою", "і", "о"));
    static final NameParadigmDef FIXED_FEM = new NameParadigmDef(
            new NameParadigmId("fixed_fem"), GrammaticalGender.FEMININE, List.of("", "", "", "", "", "", ""));

    public static final NameStyleDef NORTHERN = new NameStyleDef(
            new NameStyleId("northern"),
            "Північний",
            List.of("вел", "тор", "гар"),
            List.of("ім", "ен"),
            5000,
            List.of(new NameFinalDef("ор", MASC_HARD.id()), new NameFinalDef("і", FEM_IYA.id())));
    public static final NameStyleDef SOUTHERN = new NameStyleDef(
            new NameStyleId("southern"),
            "Південний",
            List.of("сал", "мар"),
            List.of(),
            0,
            List.of(new NameFinalDef("ан", MASC_HARD.id())));

    /** Велор / Веларор; Велена / Велія; прізвища Торвер / Гальмер (жіночі не відмінюються). */
    static final PersonNameStyleDef NORTHERN_PEOPLE = new PersonNameStyleDef(
            NORTHERN.id(),
            new NamePartsDef(List.of("вел", "тор"), List.of("ар"), 5000),
            List.of(new NameFinalDef("ор", PERSON_MASC.id())),
            List.of(new NameFinalDef("ен", FEM_HARD.id()), new NameFinalDef("і", FEM_IYA.id())),
            new NamePartsDef(List.of("торв", "гальм"), List.of(), 0),
            List.of(new SurnameFinalDef("ер", PERSON_MASC.id(), FIXED_FEM.id())));
    /** Салан / Саліна Марес. */
    static final PersonNameStyleDef SOUTHERN_PEOPLE = new PersonNameStyleDef(
            SOUTHERN.id(),
            new NamePartsDef(List.of("сал"), List.of(), 0),
            List.of(new NameFinalDef("ан", PERSON_MASC.id())),
            List.of(new NameFinalDef("ін", FEM_HARD.id())),
            new NamePartsDef(List.of("мар"), List.of(), 0),
            List.of(new SurnameFinalDef("ес", PERSON_MASC.id(), FIXED_FEM.id())));

    public static final StateFormDef REPUBLIC = form(
            "republic",
            GrammaticalGender.FEMININE,
            List.of(new IdeologyId("democracy")),
            List.of(),
            "Республіка",
            "Республіки",
            "Республіці",
            "Республіку",
            "Республікою",
            "Республіці",
            "Республіко");
    public static final StateFormDef UNITED_PROVINCES = form(
            "united_provinces",
            GrammaticalGender.PLURAL,
            List.of(),
            List.of(new SubIdeologyId("direct_democracy")),
            "Об'єднані Провінції",
            "Об'єднаних Провінцій",
            "Об'єднаним Провінціям",
            "Об'єднані Провінції",
            "Об'єднаними Провінціями",
            "Об'єднаних Провінціях",
            "Об'єднані Провінції");
    public static final StateFormDef KINGDOM = form(
            "kingdom",
            GrammaticalGender.NEUTER,
            List.of(new IdeologyId("monarchy")),
            List.of(),
            "Королівство",
            "Королівства",
            "Королівству",
            "Королівство",
            "Королівством",
            "Королівстві",
            "Королівство");

    public static final ContentPack PACK = pack(5);

    private TestNames() {}

    private static StateFormDef form(
            String id,
            GrammaticalGender gender,
            List<IdeologyId> ideologies,
            List<SubIdeologyId> subIdeologies,
            String... prefixes) {
        List<String> templates =
                Arrays.stream(prefixes).map(prefix -> prefix + " {root}").toList();
        return new StateFormDef(new StateFormId(id), gender, templates, ideologies, subIdeologies);
    }

    private static IdeologyDef ideology(String id, String... subs) {
        return new IdeologyDef(
                new IdeologyId(id),
                "Ідеологія " + id,
                100,
                List.of(),
                List.of(),
                Arrays.stream(subs)
                        .map(sub -> new SubIdeologyDef(
                                new SubIdeologyId(sub), "Підкласифікація " + sub, 100, List.of(), List.of()))
                        .toList());
    }

    /** Пакет з {@code nameCandidates} назвами-кандидатами на колесі назви. */
    public static ContentPack pack(int nameCandidates) {
        return new ContentPack(
                "0".repeat(64),
                List.of(
                        ideology("democracy", "liberal_democracy", "direct_democracy"),
                        ideology("monarchy", "absolute_monarchy")),
                List.of(new DoctrineDef(new DoctrineId("armored"), "Бронетанкова", List.of(), List.of())),
                List.of(new ResourceDef(new ResourceId("iron"), "Залізо", List.of())),
                Arrays.stream(TechBranch.values())
                        .map(branch -> new TechBranchDef(branch, "Галузь"))
                        .toList(),
                Development.levels().stream()
                        .map(level -> new DevelopmentLevelDef(level, "Рівень", "Опис", 100, 50, List.of()))
                        .toList(),
                Arrays.stream(NuclearStatus.values())
                        .map(status -> new NuclearStatusDef(status, "Статус", 100, 50, List.of()))
                        .toList(),
                List.of(new GdpLevelDef(
                        new GdpLevelId("middle"), "Рівень", "Опис", 1000, OutcomeTier.PARTIAL, 100, 50, List.of())),
                List.of(new HdiLevelDef(
                        new HdiLevelId("middle"), "Рівень", "Опис", 60, OutcomeTier.PARTIAL, 100, 50, List.of())),
                List.of(new ArmySizeDef(
                        new ArmySizeId("regular"), "Рівень", "Опис", 150, OutcomeTier.PARTIAL, 100, 50, List.of())),
                Training.levels().stream()
                        .map(level -> new TrainingLevelDef(
                                level, "Рівень", "Опис", level * 10, OutcomeTier.PARTIAL, 100, 50, List.of()))
                        .toList(),
                Arrays.stream(PersonKind.values())
                        .map(kind ->
                                new PersonKindDef(kind, "Тип", "Опис", 10, Collections.emptySortedMap(), List.of()))
                        .toList(),
                List.of(new TraitDef(new TraitId("loyal"), "Відданий", List.of(), List.of(), List.of(), List.of())),
                new NameContent(
                        List.of(MASC_HARD, FEM_IYA, PERSON_MASC, FEM_HARD, FIXED_FEM),
                        List.of(NORTHERN, SOUTHERN),
                        List.of(REPUBLIC, UNITED_PROVINCES, KINGDOM),
                        List.of(NORTHERN_PEOPLE, SOUTHERN_PEOPLE)),
                backstory(),
                balance(nameCandidates));
    }

    private static BalanceDef balance(int nameCandidates) {
        return BalanceDef.of(
                new WheelBalanceDef(50, new TreeMap<>(), List.of(10)),
                new StreakRulesDef(85, 15, 3),
                Arrays.stream(PowerCorridor.values())
                        .map(corridor ->
                                new PowerCorridorDef(corridor, new MedianRange(50, 200), new MedianRange(50, 200)))
                        .toList(),
                new GenerationBalanceDef(
                        new CountRange(2, 4),
                        new CountRange(1, 3),
                        new CountRange(2, 10),
                        10,
                        10,
                        15,
                        10,
                        10,
                        10,
                        new CountRange(1, 3),
                        new CountRange(25, 70),
                        nameCandidates));
    }

    private static BackstoryContent backstory() {
        return new BackstoryContent(
                Map.of(),
                List.of(new BackstoryFragmentDef(
                        new BackstoryFragmentId("civil_war"),
                        100,
                        15,
                        1946,
                        1966,
                        TagCondition.NONE,
                        new TreeMap<>(),
                        false,
                        List.of(),
                        0,
                        List.of(),
                        new BackstoryText("text", "Громадянська війна {year} року."))));
    }
}
