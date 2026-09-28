package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.TreeSet;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class ContentIdsTest {

    @Test
    void idsAreSnakeCase() {
        assertThat(new IdeologyId("democracy").value()).isEqualTo("democracy");
        for (Runnable invalid : new Runnable[] {
            () -> new IdeologyId("Democracy"),
            () -> new SubIdeologyId("liberal-democracy"),
            () -> new DoctrineId(""),
            () -> new ResourceId(null)
        }) {
            assertThatThrownBy(invalid::run)
                    .isInstanceOfSatisfying(
                            ValidationException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_KEY_FORMAT));
        }
    }

    @Test
    void idsSortByValueAndPrintAsValue() {
        TreeSet<ResourceId> ids = new TreeSet<>();
        ids.add(new ResourceId("uranium"));
        ids.add(new ResourceId("coal"));
        ids.add(new ResourceId("oil"));

        assertThat(ids).extracting(ResourceId::value).containsExactly("coal", "oil", "uranium");
        assertThat(new DoctrineId("armored")).hasToString("armored");
    }
}
