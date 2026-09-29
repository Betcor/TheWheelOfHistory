package kolo.engine.content;

import static kolo.engine.content.TestContent.HASH;
import static kolo.engine.content.TestContent.branches;
import static kolo.engine.content.TestContent.doctrine;
import static kolo.engine.content.TestContent.ideology;
import static kolo.engine.content.TestContent.level;
import static kolo.engine.content.TestContent.levels;
import static kolo.engine.content.TestContent.names;
import static kolo.engine.content.TestContent.nuclearStatuses;
import static kolo.engine.content.TestContent.pack;
import static kolo.engine.content.TestContent.personKinds;
import static kolo.engine.content.TestContent.resource;
import static kolo.engine.content.TestContent.trait;
import static kolo.engine.content.TestContent.traits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Development;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.OutcomeTier;
import org.junit.jupiter.api.Test;

class ContentPackTest {

    @Test
    void collectionsAreSortedByIdRegardlessOfContentOrder() {
        ContentPack pack = new ContentPack(
                HASH,
                List.of(ideology("socialism", "planned_economy"), ideology("democracy", "liberal_democracy")),
                List.of(doctrine("mountain"), doctrine("armored")),
                List.of(resource("uranium"), resource("coal")),
                branches().reversed(),
                levels().reversed(),
                nuclearStatuses().reversed(),
                TestContent.gdpLevels(),
                personKinds().reversed(),
                traits(),
                names(List.of(ideology("socialism", "planned_economy"), ideology("democracy", "liberal_democracy"))),
                TestContent.backstory(),
                TestContent.balance());

        assertThat(pack.hash()).isEqualTo(HASH);
        assertThat(pack.ideologies().keySet()).extracting(IdeologyId::value).containsExactly("democracy", "socialism");
        assertThat(pack.doctrines().keySet()).extracting(DoctrineId::value).containsExactly("armored", "mountain");
        assertThat(pack.resources().keySet()).extracting(ResourceId::value).containsExactly("coal", "uranium");
        assertThat(pack.techBranches().keySet()).containsExactly(TechBranch.values());
        assertThat(pack.developmentLevels().keySet()).containsExactly(-3, -2, -1, 0, 1, 2);
        assertThat(pack.nuclearStatuses().keySet()).containsExactly(NuclearStatus.values());
        assertThat(pack.personKinds().keySet()).containsExactly(PersonKind.values());
        assertThat(pack.balance()).isEqualTo(TestContent.balance());
    }

    @Test
    void looksUpBranchLevelAndNuclearStatus() {
        ContentPack pack = pack(List.of(ideology("democracy", "a")));

        assertThat(pack.techBranch(TechBranch.ENERGY_SCIENCE).name()).isEqualTo("Галузь energy_science");
        assertThat(pack.developmentLevel(Development.MIN)).isEqualTo(level(-3));
        assertThat(pack.developmentLevel(Development.MAX)).isEqualTo(level(2));
        assertThat(pack.nuclearStatus(NuclearStatus.ARSENAL).status()).isEqualTo(NuclearStatus.ARSENAL);
        assertThatThrownBy(() -> pack.developmentLevel(3))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @Test
    void everyBranchLevelAndNuclearStatusMustBeDefined() {
        List<IdeologyDef> ideologies = List.of(ideology("democracy", "a"));
        List<DoctrineDef> doctrines = List.of(doctrine("armored"));
        List<ResourceDef> resources = List.of(resource("iron"));

        assertMissing(
                () -> new ContentPack(
                        HASH,
                        ideologies,
                        doctrines,
                        resources,
                        branches().subList(0, 3),
                        levels(),
                        nuclearStatuses(),
                        TestContent.gdpLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.balance()),
                "tech_branches",
                "energy_science");
        assertMissing(
                () -> new ContentPack(
                        HASH,
                        ideologies,
                        doctrines,
                        resources,
                        branches(),
                        levels().subList(1, 6),
                        nuclearStatuses(),
                        TestContent.gdpLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.balance()),
                "development_levels",
                -3);
        assertMissing(
                () -> new ContentPack(
                        HASH,
                        ideologies,
                        doctrines,
                        resources,
                        branches(),
                        levels(),
                        List.of(new NuclearStatusDef(NuclearStatus.NONE, "Немає", 80, 50, List.of())),
                        TestContent.gdpLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.balance()),
                "nuclear_statuses",
                "program");
    }

    @Test
    void duplicateBranchIsReportedByContentKey() {
        List<TechBranchDef> twice = new ArrayList<>(branches());
        twice.add(new TechBranchDef(TechBranch.MILITARY, "Військо"));

        assertThatThrownBy(() -> new ContentPack(
                        HASH,
                        List.of(ideology("democracy", "a")),
                        List.of(doctrine("armored")),
                        List.of(resource("iron")),
                        twice,
                        levels(),
                        nuclearStatuses(),
                        TestContent.gdpLevels(),
                        personKinds(),
                        traits(),
                        names(List.of(ideology("democracy", "a"))),
                        TestContent.backstory(),
                        TestContent.balance()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details())
                            .containsExactly(entry("field", "tech_branch.id"), entry("value", "military"));
                });
    }

    @Test
    void looksUpDefinitionsAndOwnerOfSubIdeology() {
        ContentPack pack = pack(List.of(
                ideology("democracy", "liberal_democracy", "social_democracy"),
                ideology("totalitarianism", "revanchism")));
        SubIdeologyId revanchism = new SubIdeologyId("revanchism");

        assertThat(pack.ideology(new IdeologyId("democracy"))).isPresent();
        assertThat(pack.ideology(new IdeologyId("monarchy"))).isEmpty();
        assertThat(pack.ideologyOf(revanchism)).map(IdeologyDef::id).contains(new IdeologyId("totalitarianism"));
        assertThat(pack.subIdeology(revanchism)).map(SubIdeologyDef::id).contains(revanchism);
        assertThat(pack.subIdeology(new SubIdeologyId("technocracy"))).isEmpty();
        assertThat(pack.doctrine(new DoctrineId("armored"))).isPresent();
        assertThat(pack.resource(new ResourceId("gold"))).isEmpty();
    }

    @Test
    void subIdeologyIdsAreUniqueAcrossIdeologies() {
        assertThatThrownBy(() -> pack(List.of(ideology("democracy", "reformist"), ideology("socialism", "reformist"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details())
                            .containsExactly(entry("field", "sub_ideology.id"), entry("value", "reformist"));
                });
    }

    @Test
    void duplicateTopLevelIdsAreRejected() {
        assertThatThrownBy(() -> pack(List.of(ideology("democracy", "a"), ideology("democracy", "b"))))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID));
        assertThatThrownBy(() -> new ContentPack(
                        HASH,
                        List.of(ideology("democracy", "a")),
                        List.of(doctrine("armored"), doctrine("armored")),
                        List.of(resource("iron")),
                        branches(),
                        levels(),
                        nuclearStatuses(),
                        TestContent.gdpLevels(),
                        personKinds(),
                        traits(),
                        names(List.of(ideology("democracy", "a"))),
                        TestContent.backstory(),
                        TestContent.balance()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void everyCollectionAndHashIsRequired() {
        List<IdeologyDef> ideologies = List.of(ideology("democracy", "a"));
        List<DoctrineDef> doctrines = List.of(doctrine("armored"));
        List<ResourceDef> resources = List.of(resource("iron"));

        assertEmpty(
                () -> new ContentPack(
                        HASH,
                        List.of(),
                        doctrines,
                        resources,
                        branches(),
                        levels(),
                        nuclearStatuses(),
                        TestContent.gdpLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.balance()),
                "ideologies");
        assertEmpty(
                () -> new ContentPack(
                        HASH,
                        ideologies,
                        List.of(),
                        resources,
                        branches(),
                        levels(),
                        nuclearStatuses(),
                        TestContent.gdpLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.balance()),
                "doctrines");
        assertEmpty(
                () -> new ContentPack(
                        HASH,
                        ideologies,
                        doctrines,
                        List.of(),
                        branches(),
                        levels(),
                        nuclearStatuses(),
                        TestContent.gdpLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.balance()),
                "resources");
        assertThatThrownBy(() -> new ContentPack(
                        " ",
                        ideologies,
                        doctrines,
                        resources,
                        branches(),
                        levels(),
                        nuclearStatuses(),
                        TestContent.gdpLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.balance()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BLANK_VALUE));
    }

    @Test
    void mapsAreReadOnly() {
        ContentPack pack = pack(List.of(ideology("democracy", "a")));

        assertThatThrownBy(() -> pack.resources().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void looksUpPersonKindsAndTraits() {
        ContentPack pack = withTraits(List.of(
                trait("loyal", List.of(), "treacherous"),
                trait("treacherous", List.of()),
                trait("genius", List.of(PersonKind.SCIENTIST)),
                trait("reckless", List.of(PersonKind.GENERAL, PersonKind.ADMIRAL))));

        assertThat(pack.personKind(PersonKind.PRETENDER).name()).isEqualTo("Тип pretender");
        assertThat(pack.traits().keySet())
                .extracting(TraitId::value)
                .containsExactly("genius", "loyal", "reckless", "treacherous");
        assertThat(pack.trait(new TraitId("genius"))).isPresent();
        assertThat(pack.trait(new TraitId("coward"))).isEmpty();
        assertThat(pack.traitsFor(PersonKind.SCIENTIST))
                .extracting(t -> t.id().value())
                .containsExactly("genius", "loyal", "treacherous");
        assertThat(pack.traitsFor(PersonKind.ARTIST))
                .extracting(t -> t.id().value())
                .containsExactly("loyal", "treacherous");
    }

    @Test
    void incompatibilityIsSymmetric() {
        ContentPack pack = withTraits(List.of(
                trait("loyal", List.of(), "treacherous"), trait("treacherous", List.of()), trait("robust", List.of())));
        TraitId loyal = new TraitId("loyal");
        TraitId treacherous = new TraitId("treacherous");
        TraitId robust = new TraitId("robust");

        assertThat(pack.compatible(loyal, treacherous)).isFalse();
        assertThat(pack.compatible(treacherous, loyal)).isFalse();
        assertThat(pack.compatible(loyal, robust)).isTrue();
        assertThat(pack.compatible(robust, robust))
                .as("риса не повторюється в постаті")
                .isFalse();
        assertThatThrownBy(() -> pack.compatible(loyal, new TraitId("coward")))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    @Test
    void traitsAreValidatedAgainstEachOtherAndPersonKinds() {
        assertThatThrownBy(() -> withTraits(List.of(trait("loyal", List.of(), "treacherous"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(entry("field", "trait.loyal.incompatible"), entry("value", "treacherous"));
                });
        assertThatThrownBy(() -> withTraits(List.of(trait("loyal", List.of()), trait("loyal", List.of()))))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID));
        // Жодної риси для решти типів: генератор не зміг би дати їм риси.
        assertMissing(() -> withTraits(List.of(trait("genius", List.of(PersonKind.SCIENTIST)))), "traits", "general");
        assertEmpty(() -> withTraits(List.of()), "traits");
    }

    @Test
    void everyPersonKindMustBeDefined() {
        assertMissing(
                () -> new ContentPack(
                        HASH,
                        List.of(ideology("democracy", "a")),
                        List.of(doctrine("armored")),
                        List.of(resource("iron")),
                        branches(),
                        levels(),
                        nuclearStatuses(),
                        TestContent.gdpLevels(),
                        personKinds().subList(0, PersonKind.values().length - 1),
                        traits(),
                        names(List.of(ideology("democracy", "a"))),
                        TestContent.backstory(),
                        TestContent.balance()),
                "person_kinds",
                "pretender");
    }

    @Test
    void findsStateFormsForSubIdeology() {
        List<IdeologyDef> ideologies =
                List.of(ideology("democracy", "liberal_democracy"), ideology("monarchy", "absolute_monarchy"));
        StateFormDef republic = TestContent.republic(List.of(new IdeologyId("democracy")), List.of());
        StateFormDef kingdom = form("kingdom", List.of(), List.of(new SubIdeologyId("absolute_monarchy")));
        ContentPack pack = withNames(ideologies, List.of(republic, kingdom));

        assertThat(pack.stateFormsFor(new SubIdeologyId("liberal_democracy"))).containsExactly(republic);
        assertThat(pack.stateFormsFor(new SubIdeologyId("absolute_monarchy"))).containsExactly(kingdom);
        assertThat(pack.names().stateForms()).hasSize(2);
        assertThatThrownBy(() -> pack.stateFormsFor(new SubIdeologyId("revanchism")))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    @Test
    void stateFormsReferenceKnownIdeologiesAndCoverEverySubIdeology() {
        List<IdeologyDef> ideologies =
                List.of(ideology("democracy", "liberal_democracy"), ideology("monarchy", "absolute_monarchy"));
        StateFormDef republic = TestContent.republic(List.of(new IdeologyId("democracy")), List.of());

        assertThatThrownBy(() -> withNames(
                        ideologies,
                        List.of(republic, form("kingdom", List.of(new IdeologyId("theocracy")), List.of()))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(
                                    entry("field", "state_form.kingdom.ideologies"), entry("value", "theocracy"));
                });
        assertThatThrownBy(() -> withNames(
                        ideologies,
                        List.of(republic, form("kingdom", List.of(), List.of(new SubIdeologyId("feudal_monarchy"))))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(
                                    entry("field", "state_form.kingdom.sub_ideologies"),
                                    entry("value", "feudal_monarchy"));
                });
        // Без форми для монархії генератор не склав би їй повну назву.
        assertMissing(() -> withNames(ideologies, List.of(republic)), "state_forms", "absolute_monarchy");
    }

    @Test
    void backstoryTagsNeedASource() {
        // Джерела: мітка ідеології (democracy), ядерного статусу, словника коліс генерації й іншого фрагмента.
        List<NuclearStatusDef> statuses = List.of(
                new NuclearStatusDef(NuclearStatus.NONE, "Немає", 80, 50, List.of()),
                new NuclearStatusDef(NuclearStatus.PROGRAM, "Програма", 13, 70, List.of("nuclear_program")),
                new NuclearStatusDef(NuclearStatus.ARSENAL, "Арсенал", 7, 90, List.of("nuclear_power")));
        BackstoryFragmentDef lostWar = TestContent.fragment("lost_war", TagCondition.NONE, List.of("lost_war"));
        BackstoryFragmentDef reparations = TestContent.fragment(
                "reparations",
                new TagCondition(List.of("lost_war"), List.of("democracy", "poor"), List.of("nuclear_power")),
                List.of());

        ContentPack pack = withBackstory(
                statuses, new BackstoryContent(Map.of("poor", "Бідна країна."), List.of(lostWar, reparations)));

        assertThat(pack.backstory().fragments()).hasSize(2);
        assertThatThrownBy(() -> withBackstory(statuses, new BackstoryContent(Map.of(), List.of(lostWar, reparations))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(entry("field", "backstory.reparations.tags"), entry("value", "poor"));
                });
    }

    @Test
    void developmentLevelTagsAreBackstoryTagSources() {
        List<DevelopmentLevelDef> levels = Development.levels().stream()
                .map(level -> level == Development.MIN
                        ? new DevelopmentLevelDef(level, "Глибоке відставання", "Опис", 10, 5, List.of("backward"))
                        : TestContent.level(level))
                .toList();
        BackstoryFragmentDef stagnation = TestContent.fragment(
                "stagnation", new TagCondition(List.of("backward"), List.of(), List.of()), List.of());
        BackstoryContent backstory = new BackstoryContent(Map.of(), List.of(stagnation));

        assertThat(withBackstory(levels, TestContent.nuclearStatuses(), backstory)
                        .backstory()
                        .fragments())
                .hasSize(1);
        assertThatThrownBy(() -> withBackstory(levels(), TestContent.nuclearStatuses(), backstory))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(entry("field", "backstory.stagnation.tags"), entry("value", "backward"));
                });
    }

    @Test
    void gdpLevelsKeepContentOrderAndAreLookedUpById() {
        ContentPack pack = pack(List.of(ideology("democracy", "a")));

        assertThat(pack.gdpLevels())
                .extracting(GdpLevelDef::id)
                .containsExactly(new GdpLevelId("poor"), new GdpLevelId("middle"), new GdpLevelId("rich"));
        assertThat(pack.gdpLevel(new GdpLevelId("rich")).orElseThrow().perCapita())
                .isEqualTo(3500);
        assertThat(pack.gdpLevel(new GdpLevelId("unknown"))).isEmpty();
        assertThatThrownBy(() -> pack.gdpLevels().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void gdpLevelsMustBePresentUniqueAndGoFromPoorToRich() {
        assertEmpty(() -> withGdp(List.of()), "gdp_levels");
        assertThatThrownBy(() -> withGdp(List.of(
                        TestContent.gdpLevel("poor", 250, OutcomeTier.FAIL, List.of()),
                        TestContent.gdpLevel("poor", 500, OutcomeTier.SUCCESS, List.of()))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details()).containsExactly(entry("field", "gdp_level.id"), entry("value", "poor"));
                });
        assertThatThrownBy(() -> withGdp(TestContent.gdpLevels().reversed()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details())
                            .containsExactly(entry("field", "gdp_level.middle.per_capita"), entry("value", 1000));
                });
    }

    @Test
    void gdpLevelTagsAreBackstoryTagSources() {
        BackstoryFragmentDef reparations =
                TestContent.fragment("reparations", new TagCondition(List.of("poor"), List.of(), List.of()), List.of());
        BackstoryContent backstory = new BackstoryContent(Map.of(), List.of(reparations));
        List<GdpLevelDef> tagged = List.of(
                TestContent.gdpLevel("destitute", 100, OutcomeTier.CRIT_FAIL, List.of("poor")),
                TestContent.gdpLevel("rich", 3500, OutcomeTier.CRIT_SUCCESS, List.of("rich")));

        assertThat(withGdp(tagged, backstory).backstory().fragments()).hasSize(1);
        assertThatThrownBy(() -> withGdp(TestContent.gdpLevels(), backstory))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(entry("field", "backstory.reparations.tags"), entry("value", "poor"));
                });
    }

    private static ContentPack withGdp(List<GdpLevelDef> gdpLevels) {
        return withGdp(gdpLevels, TestContent.backstory());
    }

    private static ContentPack withGdp(List<GdpLevelDef> gdpLevels, BackstoryContent backstory) {
        List<IdeologyDef> ideologies = List.of(ideology("democracy", "a"));
        return new ContentPack(
                HASH,
                ideologies,
                List.of(doctrine("armored")),
                List.of(resource("iron")),
                branches(),
                levels(),
                nuclearStatuses(),
                gdpLevels,
                personKinds(),
                traits(),
                names(ideologies),
                backstory,
                TestContent.balance());
    }

    private static ContentPack withBackstory(List<NuclearStatusDef> statuses, BackstoryContent backstory) {
        return withBackstory(levels(), statuses, backstory);
    }

    private static ContentPack withBackstory(
            List<DevelopmentLevelDef> levels, List<NuclearStatusDef> statuses, BackstoryContent backstory) {
        List<IdeologyDef> ideologies = List.of(ideology("democracy", "a"));
        return new ContentPack(
                HASH,
                ideologies,
                List.of(doctrine("armored")),
                List.of(resource("iron")),
                branches(),
                levels,
                statuses,
                TestContent.gdpLevels(),
                personKinds(),
                traits(),
                names(ideologies),
                backstory,
                TestContent.balance());
    }

    private static StateFormDef form(String id, List<IdeologyId> ideologies, List<SubIdeologyId> subIdeologies) {
        return new StateFormDef(
                new StateFormId(id),
                GrammaticalGender.NEUTER,
                TestContent.republic(List.of(new IdeologyId("democracy")), List.of())
                        .templates(),
                ideologies,
                subIdeologies);
    }

    private static ContentPack withNames(List<IdeologyDef> ideologies, List<StateFormDef> forms) {
        return new ContentPack(
                HASH,
                ideologies,
                List.of(doctrine("armored")),
                List.of(resource("iron")),
                branches(),
                levels(),
                nuclearStatuses(),
                TestContent.gdpLevels(),
                personKinds(),
                traits(),
                new NameContent(
                        TestContent.paradigms(),
                        List.of(TestContent.style("northern")),
                        forms,
                        List.of(TestContent.personStyle("northern"))),
                TestContent.backstory(),
                TestContent.balance());
    }

    private static ContentPack withTraits(List<TraitDef> traits) {
        return new ContentPack(
                HASH,
                List.of(ideology("democracy", "a")),
                List.of(doctrine("armored")),
                List.of(resource("iron")),
                branches(),
                levels(),
                nuclearStatuses(),
                TestContent.gdpLevels(),
                personKinds(),
                traits,
                names(List.of(ideology("democracy", "a"))),
                TestContent.backstory(),
                TestContent.balance());
    }

    private static void assertMissing(Runnable create, String field, Object value) {
        assertThatThrownBy(create::run).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION);
            assertThat(e.details()).containsExactly(entry("field", field), entry("value", value));
        });
    }

    private static void assertEmpty(Runnable create, String field) {
        assertThatThrownBy(create::run).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.EMPTY_COLLECTION);
            assertThat(e.details()).containsExactly(entry("field", field));
        });
    }
}
