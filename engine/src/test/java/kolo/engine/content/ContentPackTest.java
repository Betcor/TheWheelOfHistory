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
import kolo.engine.state.Training;
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
                TestContent.hdiLevels(),
                TestContent.armySizes(),
                TestContent.trainingLevels(),
                personKinds().reversed(),
                traits(),
                names(List.of(ideology("socialism", "planned_economy"), ideology("democracy", "liberal_democracy"))),
                TestContent.backstory(),
                TestContent.streaks(),
                TestReligions.content(),
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
        assertThat(pack.religions().archetypes()).hasSize(2);
        assertThat(pack.balance().religion()).isEqualTo(TestReligions.BALANCE);
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds(),
                        traits(),
                        names(List.of(ideology("democracy", "a"))),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds(),
                        traits(),
                        names(List.of(ideology("democracy", "a"))),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds(),
                        traits(),
                        names(ideologies),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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
                        TestContent.hdiLevels(),
                        TestContent.armySizes(),
                        TestContent.trainingLevels(),
                        personKinds().subList(0, PersonKind.values().length - 1),
                        traits(),
                        names(List.of(ideology("democracy", "a"))),
                        TestContent.backstory(),
                        TestContent.streaks(),
                        TestReligions.content(),
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

    @Test
    void hdiLevelsKeepContentOrderAndAreLookedUpById() {
        ContentPack pack = pack(List.of(ideology("democracy", "a")));

        assertThat(pack.hdiLevels())
                .extracting(HdiLevelDef::id)
                .containsExactly(new HdiLevelId("low"), new HdiLevelId("middle"), new HdiLevelId("high"));
        assertThat(pack.hdiLevel(new HdiLevelId("high")).orElseThrow().hdi()).isEqualTo(80);
        assertThat(pack.hdiLevel(new HdiLevelId("unknown"))).isEmpty();
        assertThatThrownBy(() -> pack.hdiLevels().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hdiLevelsMustBePresentUniqueAndGoFromLowToHigh() {
        assertEmpty(() -> withHdi(List.of()), "hdi_levels");
        assertThatThrownBy(() -> withHdi(List.of(
                        TestContent.hdiLevel("low", 30, OutcomeTier.FAIL, List.of()),
                        TestContent.hdiLevel("low", 60, OutcomeTier.SUCCESS, List.of()))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details()).containsExactly(entry("field", "hdi_level.id"), entry("value", "low"));
                });
        assertThatThrownBy(() -> withHdi(TestContent.hdiLevels().reversed()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details()).containsExactly(entry("field", "hdi_level.middle.hdi"), entry("value", 60));
                });
    }

    @Test
    void hdiLevelTagsAreBackstoryTagSources() {
        BackstoryFragmentDef exodus = TestContent.fragment(
                "brain_drain", new TagCondition(List.of("educated"), List.of(), List.of()), List.of());
        BackstoryContent backstory = new BackstoryContent(Map.of(), List.of(exodus));
        List<HdiLevelDef> tagged = List.of(
                TestContent.hdiLevel("low", 30, OutcomeTier.CRIT_FAIL, List.of()),
                TestContent.hdiLevel("high", 80, OutcomeTier.CRIT_SUCCESS, List.of("educated")));

        assertThat(withHdi(tagged, backstory).backstory().fragments()).hasSize(1);
        assertThatThrownBy(() -> withHdi(TestContent.hdiLevels(), backstory))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(entry("field", "backstory.brain_drain.tags"), entry("value", "educated"));
                });
    }

    @Test
    void armySizesKeepContentOrderAndAreLookedUpById() {
        ContentPack pack = pack(List.of(ideology("democracy", "a")));

        assertThat(pack.armySizes())
                .extracting(ArmySizeDef::id)
                .containsExactly(new ArmySizeId("small"), new ArmySizeId("regular"), new ArmySizeId("large"));
        assertThat(pack.armySize(new ArmySizeId("large")).orElseThrow().shareBp())
                .isEqualTo(300);
        assertThat(pack.armySize(new ArmySizeId("unknown"))).isEmpty();
        assertThatThrownBy(() -> pack.armySizes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void armySizesMustBePresentUniqueAndGoFromSmallToLarge() {
        assertEmpty(() -> withArmy(List.of()), "army_sizes");
        assertThatThrownBy(() -> withArmy(List.of(
                        TestContent.armySize("small", 40, OutcomeTier.FAIL, List.of()),
                        TestContent.armySize("small", 300, OutcomeTier.SUCCESS, List.of()))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details()).containsExactly(entry("field", "army_size.id"), entry("value", "small"));
                });
        assertThatThrownBy(() -> withArmy(TestContent.armySizes().reversed()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details())
                            .containsExactly(entry("field", "army_size.regular.share_bp"), entry("value", 150));
                });
    }

    @Test
    void armySizeTagsAreBackstoryTagSources() {
        BackstoryFragmentDef parade = TestContent.fragment(
                "military_parade", new TagCondition(List.of("large_army"), List.of(), List.of()), List.of());
        BackstoryContent backstory = new BackstoryContent(Map.of(), List.of(parade));
        List<ArmySizeDef> tagged = List.of(
                TestContent.armySize("small", 40, OutcomeTier.CRIT_FAIL, List.of()),
                TestContent.armySize("large", 300, OutcomeTier.CRIT_SUCCESS, List.of("large_army")));

        assertThat(withArmy(tagged, backstory).backstory().fragments()).hasSize(1);
        assertThatThrownBy(() -> withArmy(TestContent.armySizes(), backstory))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(
                                    entry("field", "backstory.military_parade.tags"), entry("value", "large_army"));
                });
    }

    @Test
    void trainingLevelsAreKeyedByLevelFromMilitiaToElite() {
        ContentPack pack = pack(List.of(ideology("democracy", "a")));

        assertThat(pack.trainingLevels().keySet()).containsExactly(1, 2, 3, 4, 5);
        assertThat(pack.trainingLevels().values())
                .extracting(TrainingLevelDef::tier)
                .containsExactly(OutcomeTier.values());
        assertThat(pack.trainingLevel(Training.MAX).combatModifier()).isEqualTo(20);
        assertThatThrownBy(() -> pack.trainingLevel(Training.MAX + 1))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() -> pack.trainingLevels().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void everyTrainingLevelMustBeDefinedOnceFromMilitiaToElite() {
        List<TrainingLevelDef> levels = TestContent.trainingLevels();
        assertMissing(() -> withTraining(levels.subList(0, 4)), "training_levels", 5);
        assertMissing(() -> withTraining(List.of()), "training_levels", 1);
        List<TrainingLevelDef> duplicate = new ArrayList<>(levels);
        duplicate.add(TestContent.trainingLevel(5, OutcomeTier.CRIT_SUCCESS, List.of()));
        assertThatThrownBy(() -> withTraining(duplicate)).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
            assertThat(e.details()).containsExactly(entry("field", "training_level.level"), entry("value", 5));
        });
        assertThatThrownBy(() -> withTraining(levels.reversed()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details())
                            .containsExactly(entry("field", "training_level.4.level"), entry("value", 4));
                });
    }

    @Test
    void trainingTagsAreBackstoryTagSources() {
        BackstoryFragmentDef veterans = TestContent.fragment(
                "veterans_march", new TagCondition(List.of("elite_army"), List.of(), List.of()), List.of());
        BackstoryContent backstory = new BackstoryContent(Map.of(), List.of(veterans));
        List<TrainingLevelDef> tagged = new ArrayList<>(TestContent.trainingLevels());
        tagged.set(4, TestContent.trainingLevel(5, OutcomeTier.CRIT_SUCCESS, List.of("elite_army")));

        assertThat(withTraining(tagged, backstory).backstory().fragments()).hasSize(1);
        assertThatThrownBy(() -> withTraining(TestContent.trainingLevels(), backstory))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(
                                    entry("field", "backstory.veterans_march.tags"), entry("value", "elite_army"));
                });
    }

    private static ContentPack withGdp(List<GdpLevelDef> gdpLevels) {
        return withGdp(gdpLevels, TestContent.backstory());
    }

    private static ContentPack withGdp(List<GdpLevelDef> gdpLevels, BackstoryContent backstory) {
        return withLevels(
                gdpLevels, TestContent.hdiLevels(), TestContent.armySizes(), TestContent.trainingLevels(), backstory);
    }

    private static ContentPack withHdi(List<HdiLevelDef> hdiLevels) {
        return withHdi(hdiLevels, TestContent.backstory());
    }

    private static ContentPack withHdi(List<HdiLevelDef> hdiLevels, BackstoryContent backstory) {
        return withLevels(
                TestContent.gdpLevels(), hdiLevels, TestContent.armySizes(), TestContent.trainingLevels(), backstory);
    }

    private static ContentPack withArmy(List<ArmySizeDef> armySizes) {
        return withArmy(armySizes, TestContent.backstory());
    }

    private static ContentPack withArmy(List<ArmySizeDef> armySizes, BackstoryContent backstory) {
        return withLevels(
                TestContent.gdpLevels(), TestContent.hdiLevels(), armySizes, TestContent.trainingLevels(), backstory);
    }

    private static ContentPack withTraining(List<TrainingLevelDef> trainingLevels) {
        return withTraining(trainingLevels, TestContent.backstory());
    }

    private static ContentPack withTraining(List<TrainingLevelDef> trainingLevels, BackstoryContent backstory) {
        return withLevels(
                TestContent.gdpLevels(), TestContent.hdiLevels(), TestContent.armySizes(), trainingLevels, backstory);
    }

    @Test
    void looksUpStreakWheelsByKind() {
        ContentPack pack = pack(List.of(ideology("democracy", "a")));

        assertThat(pack.streaks().wheels()).containsOnlyKeys(StreakKind.GOLDEN_AGE, StreakKind.UNDERDOG);
        assertThat(pack.streaks().wheel(StreakKind.UNDERDOG).rewards())
                .extracting(StreakRewardDef::id)
                .containsExactly(new StreakRewardId("sympathy"));
    }

    @Test
    void streakWheelAndRewardTagsAreBackstoryTagSources() {
        // world_attention — мітка колеса «Золотої доби», international_sympathy — мітка нагороди «Андердога».
        BackstoryFragmentDef scrutiny = TestContent.fragment(
                "scrutiny",
                new TagCondition(List.of("world_attention"), List.of(), List.of("international_sympathy")),
                List.of());
        BackstoryContent backstory = new BackstoryContent(Map.of(), List.of(scrutiny));
        StreakContent withoutTags = new StreakContent(List.of(
                TestContent.streakWheel(
                        StreakKind.GOLDEN_AGE, List.of("world_attention"), TestContent.reward("pride", List.of())),
                TestContent.streakWheel(StreakKind.UNDERDOG, List.of(), TestContent.reward("sympathy", List.of()))));

        assertThat(withStreaks(TestContent.streaks(), backstory).backstory().fragments())
                .hasSize(1);
        assertThatThrownBy(() -> withStreaks(withoutTags, backstory))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(
                                    entry("field", "backstory.scrutiny.tags"),
                                    entry("value", "international_sympathy"));
                });
    }

    private static ContentPack withStreaks(StreakContent streaks, BackstoryContent backstory) {
        List<IdeologyDef> ideologies = List.of(ideology("democracy", "a"));
        return new ContentPack(
                HASH,
                ideologies,
                List.of(doctrine("armored")),
                List.of(resource("iron")),
                branches(),
                levels(),
                nuclearStatuses(),
                TestContent.gdpLevels(),
                TestContent.hdiLevels(),
                TestContent.armySizes(),
                TestContent.trainingLevels(),
                personKinds(),
                traits(),
                names(ideologies),
                backstory,
                streaks,
                TestReligions.content(),
                TestContent.balance());
    }

    private static ContentPack withLevels(
            List<GdpLevelDef> gdpLevels,
            List<HdiLevelDef> hdiLevels,
            List<ArmySizeDef> armySizes,
            List<TrainingLevelDef> trainingLevels,
            BackstoryContent backstory) {
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
                hdiLevels,
                armySizes,
                trainingLevels,
                personKinds(),
                traits(),
                names(ideologies),
                backstory,
                TestContent.streaks(),
                TestReligions.content(),
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
                TestContent.hdiLevels(),
                TestContent.armySizes(),
                TestContent.trainingLevels(),
                personKinds(),
                traits(),
                names(ideologies),
                backstory,
                TestContent.streaks(),
                TestReligions.content(),
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
                TestContent.hdiLevels(),
                TestContent.armySizes(),
                TestContent.trainingLevels(),
                personKinds(),
                traits(),
                new NameContent(
                        TestContent.paradigms(),
                        List.of(TestContent.style("northern")),
                        forms,
                        List.of(TestContent.personStyle("northern"))),
                TestContent.backstory(),
                TestContent.streaks(),
                TestReligions.content(),
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
                TestContent.hdiLevels(),
                TestContent.armySizes(),
                TestContent.trainingLevels(),
                personKinds(),
                traits,
                names(List.of(ideology("democracy", "a"))),
                TestContent.backstory(),
                TestContent.streaks(),
                TestReligions.content(),
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
