package kolo.engine.content;

import static kolo.engine.content.TestContent.sub;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class DefinitionsTest {

    @Test
    void ideologyNeedsAtLeastOneSubIdeology() {
        assertFails(
                () -> new IdeologyDef(new IdeologyId("democracy"), "Демократія", List.of(), List.of(), List.of()),
                ErrorCode.EMPTY_COLLECTION);
    }

    @Test
    void subIdeologiesOfOneIdeologyAreUnique() {
        assertFails(
                () -> TestContent.ideology("democracy", "liberal_democracy", "liberal_democracy"),
                ErrorCode.DUPLICATE_ID);
    }

    @Test
    void ideologyFindsItsSubIdeology() {
        IdeologyDef democracy = TestContent.ideology("democracy", "liberal_democracy", "social_democracy");

        assertThat(democracy.subIdeology(new SubIdeologyId("social_democracy"))).contains(sub("social_democracy"));
        assertThat(democracy.subIdeology(new SubIdeologyId("revanchism"))).isEmpty();
    }

    @Test
    void namesAreRequired() {
        assertFails(
                () -> new SubIdeologyDef(new SubIdeologyId("revanchism"), " ", List.of(), List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new DoctrineDef(new DoctrineId("armored"), null, List.of(), List.of()), ErrorCode.BLANK_VALUE);
        assertFails(() -> new ResourceDef(new ResourceId("iron"), "", List.of()), ErrorCode.BLANK_VALUE);
    }

    @Test
    void tagsAreSnakeCaseAndUnique() {
        assertFails(
                () -> new ResourceDef(new ResourceId("oil"), "Нафта", List.of("Energy")), ErrorCode.INVALID_KEY_FORMAT);
        assertThatThrownBy(() -> new ResourceDef(new ResourceId("oil"), "Нафта", List.of("energy", "energy")))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details())
                            .containsExactly(entry("field", "resource.oil.tags"), entry("value", "energy"));
                });
    }

    @Test
    void collectionsAreDefensivelyCopied() {
        List<String> tags = new ArrayList<>(List.of("metal"));
        ResourceDef iron = new ResourceDef(new ResourceId("iron"), "Залізо", tags);
        tags.add("strategic");

        assertThat(iron.tags()).containsExactly("metal");
        assertThatThrownBy(() -> iron.tags().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
