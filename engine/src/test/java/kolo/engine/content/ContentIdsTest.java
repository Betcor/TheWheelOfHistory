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
            () -> new ResourceId(null),
            () -> new TraitId("Brave"),
            () -> new GdpLevelId("Very Rich"),
            () -> new HdiLevelId("very-high"),
            () -> new ArmySizeId("Nation In Arms"),
            () -> new ArchetypeId("Monotheism"),
            () -> new AspectId("war god"),
            () -> new DogmaId("holy-war"),
            () -> new ReligionPolityId(""),
            () -> new FaithFormId("Path")
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
        assertThat(new GdpLevelId("middle")).hasToString("middle");
        assertThat(new GdpLevelId("middle")).isLessThan(new GdpLevelId("poor"));
        assertThat(new HdiLevelId("high")).hasToString("high");
        assertThat(new HdiLevelId("high")).isLessThan(new HdiLevelId("low"));
        assertThat(new ArmySizeId("large")).hasToString("large");
        assertThat(new ArmySizeId("large")).isLessThan(new ArmySizeId("small"));
        assertThat(new ArchetypeId("dualism")).hasToString("dualism").isLessThan(new ArchetypeId("monotheism"));
        assertThat(new AspectId("death")).hasToString("death").isLessThan(new AspectId("war"));
        assertThat(new DogmaId("asceticism")).hasToString("asceticism").isLessThan(new DogmaId("pacifism"));
        assertThat(new ReligionPolityId("communities"))
                .hasToString("communities")
                .isLessThan(new ReligionPolityId("single_church"));
        assertThat(new FaithFormId("path")).hasToString("path").isLessThan(new FaithFormId("temple"));
    }
}
