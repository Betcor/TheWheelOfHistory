package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.map.ResourceSuitabilityMap;
import kolo.engine.rng.Rng;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.UniqueElements;

class ResourceWheelPropertiesTest {

    private static final ContentPack PACK = TestResourceMaps.PACK;
    private static final ResourceSuitabilityMap MAP = TestResourceMaps.uniform(120);

    @Property(tries = 200)
    void depositsRespectCountTableAndProvinces(
            @ForAll long seed,
            @ForAll @Size(min = 1, max = 60) @UniqueElements List<@IntRange(min = 0, max = 119) Integer> provinces) {
        StartResources resources = ResourceWheel.generate(Rng.of(seed), PACK, MAP, provinces);

        // Усі чотири ресурси є в кожній провінції: родовищ рівно стільки, скільки дало колесо кількості.
        assertThat(PACK.balance()
                        .resources()
                        .deposits(provinces.size())
                        .contains(resources.deposits().size()))
                .isTrue();
        assertThat(resources.rolls()).hasSize(1 + resources.deposits().size());
        assertThat(resources.resources()).hasSameSizeAs(resources.deposits());
        assertThat(resources.deposits())
                .allSatisfy(deposit -> assertThat(new TreeSet<>(provinces)).contains(deposit.cell()));
    }

    @Property(tries = 100)
    void sameSeedGivesSameDeposits(
            @ForAll long seed, @ForAll @Size(min = 1, max = 60) List<@IntRange(min = 0, max = 119) Integer> provinces) {
        assertThat(ResourceWheel.generate(Rng.of(seed), PACK, MAP, provinces))
                .isEqualTo(ResourceWheel.generate(Rng.of(seed), PACK, MAP, provinces));
    }
}
