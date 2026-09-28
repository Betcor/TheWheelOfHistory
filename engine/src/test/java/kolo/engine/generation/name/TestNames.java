package kolo.engine.generation.name;

import java.util.Arrays;
import java.util.List;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.content.DoctrineDef;
import kolo.engine.content.DoctrineId;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.NameContent;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameParadigmDef;
import kolo.engine.content.NameParadigmId;
import kolo.engine.content.NameStyleDef;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.PersonKindDef;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.StateFormDef;
import kolo.engine.content.StateFormId;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TechBranchDef;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.state.Development;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.TechBranch;

/** Контент для тестів генератора назв: дві ідеології, два стилі, три форми державності. */
final class TestNames {

    static final NameParadigmDef MASC_HARD = new NameParadigmDef(
            new NameParadigmId("masc_hard"), GrammaticalGender.MASCULINE, List.of("", "у", "у", "", "ом", "і", "е"));
    static final NameParadigmDef FEM_IYA = new NameParadigmDef(
            new NameParadigmId("fem_iya"), GrammaticalGender.FEMININE, List.of("я", "ї", "ї", "ю", "єю", "ї", "є"));

    static final NameStyleDef NORTHERN = new NameStyleDef(
            new NameStyleId("northern"),
            "Північний",
            List.of("вел", "тор", "гар"),
            List.of("ім", "ен"),
            5000,
            List.of(new NameFinalDef("ор", MASC_HARD.id()), new NameFinalDef("і", FEM_IYA.id())));
    static final NameStyleDef SOUTHERN = new NameStyleDef(
            new NameStyleId("southern"),
            "Південний",
            List.of("сал", "мар"),
            List.of(),
            0,
            List.of(new NameFinalDef("ан", MASC_HARD.id())));

    static final StateFormDef REPUBLIC = form(
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
    static final StateFormDef UNITED_PROVINCES = form(
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
    static final StateFormDef KINGDOM = form(
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

    static final ContentPack PACK = pack();

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
                List.of(),
                List.of(),
                Arrays.stream(subs)
                        .map(sub -> new SubIdeologyDef(
                                new SubIdeologyId(sub), "Підкласифікація " + sub, List.of(), List.of()))
                        .toList());
    }

    private static ContentPack pack() {
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
                        .map(level -> new DevelopmentLevelDef(level, "Рівень", "Опис"))
                        .toList(),
                Arrays.stream(NuclearStatus.values())
                        .map(status -> new NuclearStatusDef(status, "Статус", List.of()))
                        .toList(),
                Arrays.stream(PersonKind.values())
                        .map(kind -> new PersonKindDef(kind, "Тип", "Опис", List.of()))
                        .toList(),
                List.of(new TraitDef(new TraitId("loyal"), "Відданий", List.of(), List.of(), List.of(), List.of())),
                new NameContent(
                        List.of(MASC_HARD, FEM_IYA),
                        List.of(NORTHERN, SOUTHERN),
                        List.of(REPUBLIC, UNITED_PROVINCES, KINGDOM)));
    }
}
