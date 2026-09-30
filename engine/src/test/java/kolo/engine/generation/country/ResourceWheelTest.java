package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.ResourceId;
import kolo.engine.content.TestResources;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.ResourceSuitabilityMap;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResourceWheelTest {

    private static final ContentPack PACK = TestResourceMaps.PACK;
    private static final List<Integer> SMALL_CELLS = List.of(0, 1, 2, 3);

    @Test
    void countSectorsFollowProvinceTable() {
        assertThat(ResourceWheel.countSectors(PACK, 4))
                .extracting(Sector::id)
                .containsExactly("resources_1", "resources_2");
        assertThat(ResourceWheel.countSectors(PACK, 21))
                .extracting(Sector::id)
                .containsExactly("resources_2", "resources_3");
        // Більше, ніж в останньому рядку, — як в останньому.
        assertThat(ResourceWheel.countSectors(PACK, 5000))
                .extracting(Sector::value)
                .containsExactly(2, 3);
        assertThat(ResourceWheel.countSectors(PACK, 4)).allSatisfy(sector -> {
            assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL);
            assertThat(sector.quality()).isEqualTo(ResourceWheel.QUALITY);
        });
    }

    @Test
    void resourceSectorsAreWeightedBySuitabilityOfCountryProvinces() {
        List<Sector<ResourceId>> sectors = ResourceWheel.resourceSectors(
                PACK, TestResourceMaps.SMALL, new TreeSet<>(SMALL_CELLS), new TreeSet<>());

        // За id ресурсу; сіль і прянощі — ніде в державі. Зерно 60 : руда 60 : ліс 50 від 10 000 вниз.
        assertThat(sectors)
                .extracting(Sector::value)
                .containsExactly(TestResources.GRAIN, TestResources.ORE, TestResources.WOOD);
        assertThat(sectors).extracting(Sector::weightBp).containsExactly(3529, 3529, 2941);
        assertThat(sectors).allSatisfy(sector -> {
            assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL);
            assertThat(sector.quality()).isEqualTo(ResourceWheel.QUALITY);
        });

        // Лише комірка 1 — руда 20 проти лісу 50.
        assertThat(ResourceWheel.resourceSectors(
                        PACK, TestResourceMaps.SMALL, new TreeSet<>(List.of(1)), new TreeSet<>()))
                .extracting(Sector::weightBp)
                .containsExactly(2857, 7142);
    }

    @Test
    void chosenResourcesLeaveTheWheel() {
        List<Sector<ResourceId>> sectors = ResourceWheel.resourceSectors(
                PACK,
                TestResourceMaps.SMALL,
                new TreeSet<>(SMALL_CELLS),
                new TreeSet<>(List.of(TestResources.ORE, TestResources.GRAIN)));

        assertThat(sectors).extracting(Sector::value).containsExactly(TestResources.WOOD);
    }

    @Test
    void tinyShareStaysOnTheWheel() {
        TreeMap<Integer, Map<ResourceId, Integer>> values = new TreeMap<>();
        values.put(0, Map.of(TestResources.ORE, 1));
        for (int cell = 1; cell <= 100; cell++) {
            values.put(cell, Map.of(TestResources.WOOD, 300));
        }
        ResourceSuitabilityMap map = TestResourceMaps.map(values);

        List<Sector<ResourceId>> sectors =
                ResourceWheel.resourceSectors(PACK, map, new TreeSet<>(values.keySet()), new TreeSet<>());

        // 1 з 30 001 дав би 0 bp — округлення не прибирає ресурс з колеса.
        assertThat(sectors).extracting(Sector::weightBp).containsExactly(1, 9999);
    }

    @Test
    void depositsAreDistinctAndLieInSuitableCountryProvinces() {
        for (long seed = 0; seed < 300; seed++) {
            StartResources resources = ResourceWheel.generate(Rng.of(seed), PACK, TestResourceMaps.SMALL, SMALL_CELLS);

            assertThat(resources.deposits()).hasSizeBetween(1, 2);
            assertThat(resources.resources()).hasSameSizeAs(resources.deposits());
            for (StartDeposit deposit : resources.deposits()) {
                assertThat(TestResourceMaps.SMALL.suitability(deposit.cell(), deposit.resource()))
                        .isPositive();
            }
        }
    }

    @Test
    void depositGoesOnlyWhereTheResourceIs() {
        for (long seed = 0; seed < 200; seed++) {
            for (StartDeposit deposit : ResourceWheel.generate(Rng.of(seed), PACK, TestResourceMaps.SMALL, SMALL_CELLS)
                    .deposits()) {
                if (deposit.resource().equals(TestResources.WOOD)) {
                    assertThat(deposit.cell()).isEqualTo(1);
                } else if (deposit.resource().equals(TestResources.GRAIN)) {
                    assertThat(deposit.cell()).isEqualTo(2);
                } else {
                    assertThat(deposit.cell()).isIn(0, 1);
                }
            }
        }
    }

    @Test
    void fewerDepositsWhenResourcesRunOut() {
        ContentPack pack = TestNames.pack(
                TestResources.RESOURCES, TestResources.balance(new CountRange(3, 3), new CountRange(3, 3)));

        StartResources resources = ResourceWheel.generate(Rng.of(1), pack, TestResourceMaps.SMALL, List.of(2, 3));

        // У державі лише зерно: одне родовище замість трьох.
        assertThat(resources.deposits()).containsExactly(new StartDeposit(TestResources.GRAIN, 2));
        assertThat(resources.rolls()).hasSize(2);
    }

    @Test
    void countryWithoutAnySuitableProvinceGetsNoDeposits() {
        StartResources resources = ResourceWheel.generate(Rng.of(5), PACK, TestResourceMaps.SMALL, List.of(3));

        assertThat(resources.deposits()).isEmpty();
        assertThat(resources.rolls()).extracting(RollRecord::kind).containsExactly(ResourceWheel.COUNT_KIND);
    }

    @Test
    void rollsGoInOrderWithoutAdvantage() {
        StartResources resources = ResourceWheel.generate(Rng.of(3), PACK, TestResourceMaps.uniform(30), range(30));

        assertThat(resources.rolls()).hasSize(1 + resources.deposits().size());
        assertThat(resources.rolls().getFirst().kind()).isEqualTo(ResourceWheel.COUNT_KIND);
        assertThat(resources.rolls().subList(1, resources.rolls().size()))
                .extracting(RollRecord::kind)
                .containsOnly(ResourceWheel.RESOURCE_KIND);
        assertThat(resources.rolls())
                .allSatisfy(roll -> assertThat(roll.advantage()).isZero());
        for (int i = 0; i < resources.deposits().size(); i++) {
            assertThat(resources.rolls().get(i + 1).resultSectorId())
                    .isEqualTo(resources.deposits().get(i).resource().value());
        }
    }

    @Test
    void provinceOrderAndRepeatsDoNotMatter() {
        List<Integer> shuffled = List.of(3, 1, 1, 2, 0, 3);

        assertThat(ResourceWheel.generate(Rng.of(9), PACK, TestResourceMaps.SMALL, shuffled))
                .isEqualTo(ResourceWheel.generate(Rng.of(9), PACK, TestResourceMaps.SMALL, SMALL_CELLS));
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageDoesNotChangeResourceWheels(int advantage) {
        List<Sector<Integer>> count = ResourceWheel.countSectors(PACK, 50);
        List<Sector<ResourceId>> resources = ResourceWheel.resourceSectors(
                PACK, TestResourceMaps.SMALL, new TreeSet<>(SMALL_CELLS), new TreeSet<>());

        assertThat(Wheel.applyAdvantage(count, advantage, Wheel.MAX_STRENGTH))
                .isEqualTo(Wheel.applyAdvantage(count, 0, Wheel.MAX_STRENGTH));
        assertThat(Wheel.applyAdvantage(resources, advantage, Wheel.MAX_STRENGTH))
                .isEqualTo(Wheel.applyAdvantage(resources, 0, Wheel.MAX_STRENGTH));
    }

    @Test
    void invalidProvincesAreRejected() {
        assertThatThrownBy(() -> ResourceWheel.generate(Rng.of(1), PACK, TestResourceMaps.SMALL, List.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMPTY_COLLECTION));
        // Комірка 10 — не суходіл цієї карти.
        assertThatThrownBy(() -> ResourceWheel.generate(Rng.of(1), PACK, TestResourceMaps.SMALL, List.of(0, 10)))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @Test
    void startResourcesRejectRepeatedResource() {
        assertThatThrownBy(() -> new StartResources(
                        List.of(new StartDeposit(TestResources.ORE, 0), new StartDeposit(TestResources.ORE, 1)),
                        List.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID));
        assertThatThrownBy(() -> new StartDeposit(TestResources.ORE, -1)).isInstanceOf(ValidationException.class);
    }

    static List<Integer> range(int cells) {
        return IntStream.range(0, cells).boxed().toList();
    }
}
