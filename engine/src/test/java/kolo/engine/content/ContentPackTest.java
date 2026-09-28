package kolo.engine.content;

import static kolo.engine.content.TestContent.HASH;
import static kolo.engine.content.TestContent.branches;
import static kolo.engine.content.TestContent.doctrine;
import static kolo.engine.content.TestContent.ideology;
import static kolo.engine.content.TestContent.level;
import static kolo.engine.content.TestContent.levels;
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
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Development;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.TechBranch;
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
                personKinds().reversed(),
                traits());

        assertThat(pack.hash()).isEqualTo(HASH);
        assertThat(pack.ideologies().keySet()).extracting(IdeologyId::value).containsExactly("democracy", "socialism");
        assertThat(pack.doctrines().keySet()).extracting(DoctrineId::value).containsExactly("armored", "mountain");
        assertThat(pack.resources().keySet()).extracting(ResourceId::value).containsExactly("coal", "uranium");
        assertThat(pack.techBranches().keySet()).containsExactly(TechBranch.values());
        assertThat(pack.developmentLevels().keySet()).containsExactly(-3, -2, -1, 0, 1, 2);
        assertThat(pack.nuclearStatuses().keySet()).containsExactly(NuclearStatus.values());
        assertThat(pack.personKinds().keySet()).containsExactly(PersonKind.values());
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
                        personKinds(),
                        traits()),
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
                        personKinds(),
                        traits()),
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
                        List.of(new NuclearStatusDef(NuclearStatus.NONE, "Немає", List.of())),
                        personKinds(),
                        traits()),
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
                        personKinds(),
                        traits()))
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
                        personKinds(),
                        traits()))
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
                        personKinds(),
                        traits()),
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
                        personKinds(),
                        traits()),
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
                        personKinds(),
                        traits()),
                "resources");
        assertThatThrownBy(() -> new ContentPack(
                        " ",
                        ideologies,
                        doctrines,
                        resources,
                        branches(),
                        levels(),
                        nuclearStatuses(),
                        personKinds(),
                        traits()))
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
                        personKinds().subList(0, PersonKind.values().length - 1),
                        traits()),
                "person_kinds",
                "pretender");
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
                personKinds(),
                traits);
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
