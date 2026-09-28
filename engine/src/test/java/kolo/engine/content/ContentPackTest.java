package kolo.engine.content;

import static kolo.engine.content.TestContent.HASH;
import static kolo.engine.content.TestContent.doctrine;
import static kolo.engine.content.TestContent.ideology;
import static kolo.engine.content.TestContent.pack;
import static kolo.engine.content.TestContent.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class ContentPackTest {

    @Test
    void collectionsAreSortedByIdRegardlessOfContentOrder() {
        ContentPack pack = new ContentPack(
                HASH,
                List.of(ideology("socialism", "planned_economy"), ideology("democracy", "liberal_democracy")),
                List.of(doctrine("mountain"), doctrine("armored")),
                List.of(resource("uranium"), resource("coal")));

        assertThat(pack.hash()).isEqualTo(HASH);
        assertThat(pack.ideologies().keySet()).extracting(IdeologyId::value).containsExactly("democracy", "socialism");
        assertThat(pack.doctrines().keySet()).extracting(DoctrineId::value).containsExactly("armored", "mountain");
        assertThat(pack.resources().keySet()).extracting(ResourceId::value).containsExactly("coal", "uranium");
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
                        List.of(resource("iron"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void everyCollectionAndHashIsRequired() {
        List<IdeologyDef> ideologies = List.of(ideology("democracy", "a"));
        List<DoctrineDef> doctrines = List.of(doctrine("armored"));
        List<ResourceDef> resources = List.of(resource("iron"));

        assertEmpty(() -> new ContentPack(HASH, List.of(), doctrines, resources), "ideologies");
        assertEmpty(() -> new ContentPack(HASH, ideologies, List.of(), resources), "doctrines");
        assertEmpty(() -> new ContentPack(HASH, ideologies, doctrines, List.of()), "resources");
        assertThatThrownBy(() -> new ContentPack(" ", ideologies, doctrines, resources))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BLANK_VALUE));
    }

    @Test
    void mapsAreReadOnly() {
        ContentPack pack = pack(List.of(ideology("democracy", "a")));

        assertThatThrownBy(() -> pack.resources().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertEmpty(Runnable create, String field) {
        assertThatThrownBy(create::run).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.EMPTY_COLLECTION);
            assertThat(e.details()).containsExactly(entry("field", field));
        });
    }
}
