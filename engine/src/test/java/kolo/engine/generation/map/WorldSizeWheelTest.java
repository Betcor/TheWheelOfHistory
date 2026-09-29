package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.MapTemplateId;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldSizeWheelTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final int SEEDS = 300;
    private static final MapTemplateId PANGAEA = TestMaps.PANGAEA.id();
    private static final MapTemplateId ARCHIPELAGO = TestMaps.ARCHIPELAGO.id();

    @Test
    void npcArePlayersPlusWheelOfTheShare() {
        for (long seed = 0; seed < SEEDS; seed++) {
            WorldSize size = WorldSizeWheel.generate(Rng.of(seed), PACK, WorldSizeInput.of(3, NpcShare.NORMAL));

            assertThat(size.npc() - 3).isBetween(2, 4);
            assertThat(size.rolls().getFirst().resultSectorId()).isEqualTo("npc_" + (size.npc() - 3));
            assertThat(size.countries()).isEqualTo(3 + size.npc());
        }
    }

    @Test
    void countriesAreCappedAtTheLimit() {
        // 16 гравців + (16 + 10..20) NPC — більше за 40: NPC лишається 24.
        for (long seed = 0; seed < SEEDS; seed++) {
            WorldSize size = WorldSizeWheel.generate(Rng.of(seed), PACK, WorldSizeInput.of(16, NpcShare.MANY));

            assertThat(size.npc()).isEqualTo(WorldLimits.MAX_COUNTRIES - 16);
            assertThat(size.countries()).isEqualTo(WorldLimits.MAX_COUNTRIES);
        }
    }

    @Test
    void rollsGoInOrderWithoutAdvantage() {
        WorldSize size = WorldSizeWheel.generate(Rng.of(7), PACK, WorldSizeInput.of(2, NpcShare.FEW));

        assertThat(size.rolls())
                .extracting(RollRecord::kind)
                .containsExactly(
                        WorldSizeWheel.NPC_KIND,
                        WorldSizeWheel.TEMPLATE_KIND,
                        WorldSizeWheel.CONTINENTS_KIND,
                        WorldSizeWheel.PROVINCES_KIND,
                        WorldSizeWheel.UNCLAIMED_KIND);
        assertThat(size.rolls()).allSatisfy(roll -> {
            assertThat(roll.advantage()).isZero();
            assertThat(roll.modifiers()).isEmpty();
            assertThat(roll.turn()).isZero();
            assertThat(roll.sectors()).allSatisfy(sector -> {
                assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL);
                assertThat(sector.quality()).isEqualTo(WorldSizeWheel.QUALITY);
            });
        });
        assertThat(size.rolls().get(1).resultSectorId())
                .isEqualTo(size.template().value());
        assertThat(size.rolls().get(2).resultSectorId()).isEqualTo("continents_" + size.continents());
        assertThat(size.rolls().get(3).resultSectorId()).isEqualTo("provinces_" + size.provincesPerCountry());
        assertThat(size.rolls().get(4).resultSectorId()).isEqualTo("unclaimed_" + size.unclaimedBp());
    }

    @Test
    void resultsStayWithinContentAndBalance() {
        for (long seed = 0; seed < SEEDS; seed++) {
            WorldSize size = WorldSizeWheel.generate(Rng.of(seed), PACK, WorldSizeInput.of(4, NpcShare.NORMAL));
            MapTemplateDef template = PACK.map().template(size.template()).orElseThrow();

            assertThat(template.continents().contains(size.continents())).isTrue();
            assertThat(size.provincesPerCountry()).isIn(60, 80, 100);
            assertThat(size.unclaimedBp()).isIn(500, 1000, 1500);
            long raw = (long) size.countries() * size.provincesPerCountry() * template.provincesPct() / 100;
            assertThat(size.provinces()).isEqualTo(Math.clamp(raw, 100, 3000));
        }
    }

    @Test
    void provincesAreClampedToBalanceLimits() {
        ContentPack pack = TestNames.pack(TestMaps.CONTENT, TestMaps.world(new CountRange(1000, 1200)));
        for (long seed = 0; seed < SEEDS; seed++) {
            // Найбільше 1 + 2 держави × 100 × 150% = 450 — нижче межі.
            WorldSize small = WorldSizeWheel.generate(Rng.of(seed), pack, WorldSizeInput.of(1, NpcShare.FEW));
            // Щонайменше 40 × 60 = 2400 — вище межі.
            WorldSize large = WorldSizeWheel.generate(Rng.of(seed), pack, WorldSizeInput.of(16, NpcShare.MANY));

            assertThat(small.provinces()).isEqualTo(1000);
            assertThat(large.provinces()).isEqualTo(1200);
        }
    }

    @Test
    void fixedTemplateIsNotSpun() {
        for (long seed = 0; seed < SEEDS; seed++) {
            WorldSize size = WorldSizeWheel.generate(
                    Rng.of(seed),
                    PACK,
                    new WorldSizeInput(2, NpcShare.NORMAL, Optional.of(ARCHIPELAGO), OptionalInt.empty()));

            assertThat(size.template()).isEqualTo(ARCHIPELAGO);
            assertThat(size.continents()).isBetween(3, 5);
            assertThat(kinds(size)).doesNotContain(WorldSizeWheel.TEMPLATE_KIND);
        }
    }

    @Test
    void fixedContinentsAreNotSpunAndLeaveOnlyFittingTemplates() {
        WorldSize four = WorldSizeWheel.generate(
                Rng.of(1), PACK, new WorldSizeInput(2, NpcShare.NORMAL, Optional.empty(), OptionalInt.of(4)));
        WorldSize one = WorldSizeWheel.generate(
                Rng.of(1), PACK, new WorldSizeInput(2, NpcShare.NORMAL, Optional.empty(), OptionalInt.of(1)));

        assertThat(four.template()).isEqualTo(ARCHIPELAGO);
        assertThat(four.continents()).isEqualTo(4);
        assertThat(four.rolls().get(1).sectors())
                .singleElement()
                .satisfies(sector -> assertThat(sector.id()).isEqualTo("archipelago"));
        assertThat(kinds(four)).doesNotContain(WorldSizeWheel.CONTINENTS_KIND);
        assertThat(one.template()).isEqualTo(PANGAEA);
        assertThat(one.continents()).isEqualTo(1);
    }

    @Test
    void fixedTemplateAndContinentsSpinNeither() {
        WorldSize size = WorldSizeWheel.generate(
                Rng.of(3), PACK, new WorldSizeInput(2, NpcShare.FEW, Optional.of(ARCHIPELAGO), OptionalInt.of(5)));

        assertThat(size.template()).isEqualTo(ARCHIPELAGO);
        assertThat(size.continents()).isEqualTo(5);
        assertThat(kinds(size))
                .containsExactly(WorldSizeWheel.NPC_KIND, WorldSizeWheel.PROVINCES_KIND, WorldSizeWheel.UNCLAIMED_KIND);
    }

    @Test
    void fixedParametersDoNotShiftOtherWheels() {
        for (long seed = 0; seed < SEEDS; seed++) {
            WorldSize free = WorldSizeWheel.generate(Rng.of(seed), PACK, WorldSizeInput.of(5, NpcShare.NORMAL));
            WorldSize fixed = WorldSizeWheel.generate(
                    Rng.of(seed),
                    PACK,
                    new WorldSizeInput(5, NpcShare.NORMAL, Optional.of(PANGAEA), OptionalInt.of(1)));

            assertThat(fixed.npc()).isEqualTo(free.npc());
            assertThat(fixed.provincesPerCountry()).isEqualTo(free.provincesPerCountry());
            assertThat(fixed.unclaimedBp()).isEqualTo(free.unclaimedBp());
        }
    }

    @Test
    void unknownFixedTemplateIsRejected() {
        WorldSizeInput input = new WorldSizeInput(
                2, NpcShare.NORMAL, Optional.of(new MapTemplateId("ring_world")), OptionalInt.empty());

        assertThatThrownBy(() -> WorldSizeWheel.generate(Rng.of(1), PACK, input))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details()).containsEntry("value", "ring_world");
                });
    }

    @Test
    void continentsOutsideFixedTemplateAreRejected() {
        WorldSizeInput input = new WorldSizeInput(2, NpcShare.NORMAL, Optional.of(PANGAEA), OptionalInt.of(3));

        assertThatThrownBy(() -> WorldSizeWheel.generate(Rng.of(1), PACK, input))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details())
                            .containsEntry("field", "continents")
                            .containsEntry("min", 1)
                            .containsEntry("max", 1);
                });
    }

    @Test
    void continentsNoTemplateGivesAreRejected() {
        WorldSizeInput input = new WorldSizeInput(2, NpcShare.NORMAL, Optional.empty(), OptionalInt.of(2));

        assertThatThrownBy(() -> WorldSizeWheel.generate(Rng.of(1), PACK, input))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, WorldLimits.MAX_PLAYERS + 1})
    void playersOutsideLimitsAreRejected(int players) {
        assertThatThrownBy(() -> WorldSizeInput.of(players, NpcShare.NORMAL))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, MapTemplateDef.MAX_CONTINENTS + 1})
    void fixedContinentsOutsideLimitsAreRejected(int continents) {
        assertThatThrownBy(() -> new WorldSizeInput(2, NpcShare.NORMAL, Optional.empty(), OptionalInt.of(continents)))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @Test
    void singlePlayerWorldIsAllowed() {
        WorldSize size = WorldSizeWheel.generate(Rng.of(11), PACK, WorldSizeInput.of(1, NpcShare.FEW));

        assertThat(size.players()).isEqualTo(1);
        assertThat(size.npc()).isBetween(1, 2);
    }

    @Test
    void sectorsAreEqualAndNamedByValue() {
        assertThat(WorldSizeWheel.rangeSectors("npc_", new CountRange(2, 4)))
                .extracting(Sector::id)
                .containsExactly("npc_2", "npc_3", "npc_4");
        List<Sector<Integer>> values = WorldSizeWheel.valueSectors("unclaimed_", List.of(500, 1000));
        assertThat(values).extracting(Sector::id).containsExactly("unclaimed_500", "unclaimed_1000");
        assertThat(values).extracting(Sector::value).containsExactly(500, 1000);
        assertThat(values).extracting(Sector::weightBp).containsOnly(1);
        assertThat(WorldSizeWheel.templateSectors(PACK.map().templates()))
                .extracting(Sector::id)
                .containsExactly("pangaea", "archipelago");
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageDoesNotChangeWorldWheels(int advantage) {
        // Сектори розміру світу — PARTIAL: навіть гранична перевага лишає рівні ваги.
        assertThat(Wheel.applyAdvantage(
                        WorldSizeWheel.templateSectors(PACK.map().templates()), advantage, Wheel.MAX_STRENGTH))
                .extracting(Sector::weightBp)
                .containsExactly(5000, 5000);
        assertThat(Wheel.applyAdvantage(
                        WorldSizeWheel.rangeSectors("npc_", new CountRange(1, 4)), advantage, Wheel.MAX_STRENGTH))
                .extracting(Sector::weightBp)
                .containsExactly(2500, 2500, 2500, 2500);
    }

    private static List<WheelKind> kinds(WorldSize size) {
        return size.rolls().stream().map(RollRecord::kind).toList();
    }
}
