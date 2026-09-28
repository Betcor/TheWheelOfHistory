package kolo.engine.content;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.Development;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.Stat;
import kolo.engine.state.TechBranch;

/** Мінімальні валідні визначення для тестів моделі контенту. */
final class TestContent {

    static final String HASH = "0".repeat(64);

    private TestContent() {}

    static SubIdeologyDef sub(String id) {
        return new SubIdeologyDef(new SubIdeologyId(id), "Підкласифікація " + id, List.of(), List.of());
    }

    static IdeologyDef ideology(String id, String... subIds) {
        List<SubIdeologyDef> subs = Arrays.stream(subIds).map(TestContent::sub).toList();
        return new IdeologyDef(
                new IdeologyId(id),
                "Ідеологія " + id,
                List.of(new ModifierDef(ModifierTarget.stat(Stat.HDI), 5)),
                List.of(id),
                subs);
    }

    static DoctrineDef doctrine(String id) {
        return new DoctrineDef(new DoctrineId(id), "Доктрина " + id, List.of(), List.of("land"));
    }

    static ResourceDef resource(String id) {
        return new ResourceDef(new ResourceId(id), "Ресурс " + id, List.of("metal"));
    }

    /** По визначенню на кожну галузь. */
    static List<TechBranchDef> branches() {
        return Arrays.stream(TechBranch.values())
                .map(branch -> new TechBranchDef(branch, "Галузь " + branch.key()))
                .toList();
    }

    static DevelopmentLevelDef level(int level) {
        return new DevelopmentLevelDef(level, "Рівень " + level, "Опис рівня " + level);
    }

    /** По визначенню на кожен рівень розвиненості. */
    static List<DevelopmentLevelDef> levels() {
        return Development.levels().stream().map(TestContent::level).toList();
    }

    /** По визначенню на кожен ядерний статус. */
    static List<NuclearStatusDef> nuclearStatuses() {
        return Arrays.stream(NuclearStatus.values())
                .map(status -> new NuclearStatusDef(status, "Статус " + status.key(), List.of()))
                .toList();
    }

    /** По визначенню на кожен тип постаті. */
    static List<PersonKindDef> personKinds() {
        return Arrays.stream(PersonKind.values())
                .map(kind -> new PersonKindDef(kind, "Тип " + kind.key(), "Опис типу " + kind.key(), List.of()))
                .toList();
    }

    static TraitDef trait(String id, List<PersonKind> kinds, String... incompatible) {
        return new TraitDef(
                new TraitId(id),
                "Риса " + id,
                kinds,
                List.of(),
                List.of("positive"),
                Arrays.stream(incompatible).map(TraitId::new).toList());
    }

    /** Одна риса для будь-якого типу постаті. */
    static List<TraitDef> traits() {
        return List.of(trait("charismatic", List.of()));
    }

    /** «Велор, Велору, Велору, Велор, Велором, Велорі, Велоре». */
    static NameParadigmDef mascHard() {
        return new NameParadigmDef(
                new NameParadigmId("masc_hard"),
                GrammaticalGender.MASCULINE,
                List.of("", "у", "у", "", "ом", "і", "е"));
    }

    static NameStyleDef style(String id) {
        return new NameStyleDef(
                new NameStyleId(id),
                "Стиль " + id,
                List.of("вел", "тор"),
                List.of("ім"),
                5000,
                List.of(new NameFinalDef("ор", new NameParadigmId("masc_hard"))));
    }

    /** «Республіка {root}» у всіх відмінках. */
    static StateFormDef republic(List<IdeologyId> ideologies, List<SubIdeologyId> subIdeologies) {
        return new StateFormDef(
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
                ideologies,
                subIdeologies);
    }

    /** Назви з однією формою державності, доступною всім ідеологіям. */
    static NameContent names(List<IdeologyDef> ideologies) {
        List<IdeologyId> ids = ideologies.stream().map(IdeologyDef::id).toList();
        return new NameContent(List.of(mascHard()), List.of(style("northern")), List.of(republic(ids, List.of())));
    }

    /** Фрагмент без умов, мало що дає; тести замінюють потрібні поля. */
    static BackstoryFragmentDef fragment(String id, TagCondition condition, List<String> adds) {
        return new BackstoryFragmentDef(
                new BackstoryFragmentId(id),
                100,
                50,
                1950,
                1960,
                condition,
                new TreeMap<>(),
                false,
                adds,
                0,
                List.of(new ModifierDef(ModifierTarget.stat(Stat.STABILITY), -3)),
                new BackstoryText("text", "У {year} році в {country.locative} сталася подія."));
    }

    /** Один фрагмент без умов і без словника міток. */
    static BackstoryContent backstory() {
        return new BackstoryContent(Map.of(), List.of(fragment("civil_war", TagCondition.NONE, List.of("civil_war"))));
    }

    static ContentPack pack(List<IdeologyDef> ideologies) {
        return new ContentPack(
                HASH,
                ideologies,
                List.of(doctrine("armored")),
                List.of(resource("iron")),
                branches(),
                levels(),
                nuclearStatuses(),
                personKinds(),
                traits(),
                names(ideologies),
                backstory());
    }
}
