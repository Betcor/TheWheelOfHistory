package kolo.engine.content;

import static kolo.engine.content.TestContent.HASH;
import static kolo.engine.content.TestContent.branches;
import static kolo.engine.content.TestContent.doctrine;
import static kolo.engine.content.TestContent.ideology;
import static kolo.engine.content.TestContent.level;
import static kolo.engine.content.TestContent.levels;
import static kolo.engine.content.TestContent.nuclearStatuses;
import static kolo.engine.content.TestContent.pack;
import static kolo.engine.content.TestContent.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Development;
import kolo.engine.state.NuclearStatus;
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
                nuclearStatuses().reversed());

        assertThat(pack.hash()).isEqualTo(HASH);
        assertThat(pack.ideologies().keySet()).extracting(IdeologyId::value).containsExactly("democracy", "socialism");
        assertThat(pack.doctrines().keySet()).extracting(DoctrineId::value).containsExactly("armored", "mountain");
        assertThat(pack.resources().keySet()).extracting(ResourceId::value).containsExactly("coal", "uranium");
        assertThat(pack.techBranches().keySet()).containsExactly(TechBranch.values());
        assertThat(pack.developmentLevels().keySet()).containsExactly(-3, -2, -1, 0, 1, 2);
        assertThat(pack.nuclearStatuses().keySet()).containsExactly(NuclearStatus.values());
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
                        HASH, ideologies, doctrines, resources, branches().subList(0, 3), levels(), nuclearStatuses()),
                "tech_branches",
                "energy_science");
        assertMissing(
                () -> new ContentPack(
                        HASH, ideologies, doctrines, resources, branches(), levels().subList(1, 6), nuclearStatuses()),
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
                        List.of(new NuclearStatusDef(NuclearStatus.NONE, "Немає", List.of()))),
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
                        nuclearStatuses()))
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
                        nuclearStatuses()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void everyCollectionAndHashIsRequired() {
        List<IdeologyDef> ideologies = List.of(ideology("democracy", "a"));
        List<DoctrineDef> doctrines = List.of(doctrine("armored"));
        List<ResourceDef> resources = List.of(resource("iron"));

        assertEmpty(
                () -> new ContentPack(HASH, List.of(), doctrines, resources, branches(), levels(), nuclearStatuses()),
                "ideologies");
        assertEmpty(
                () -> new ContentPack(HASH, ideologies, List.of(), resources, branches(), levels(), nuclearStatuses()),
                "doctrines");
        assertEmpty(
                () -> new ContentPack(HASH, ideologies, doctrines, List.of(), branches(), levels(), nuclearStatuses()),
                "resources");
        assertThatThrownBy(() ->
                        new ContentPack(" ", ideologies, doctrines, resources, branches(), levels(), nuclearStatuses()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BLANK_VALUE));
    }

    @Test
    void mapsAreReadOnly() {
        ContentPack pack = pack(List.of(ideology("democracy", "a")));

        assertThatThrownBy(() -> pack.resources().clear()).isInstanceOf(UnsupportedOperationException.class);
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
