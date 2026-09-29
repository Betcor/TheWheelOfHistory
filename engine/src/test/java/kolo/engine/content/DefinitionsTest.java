package kolo.engine.content;

import static kolo.engine.content.TestContent.sub;
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
import kolo.engine.wheel.OutcomeTier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class DefinitionsTest {

    @Test
    void ideologyNeedsAtLeastOneSubIdeology() {
        assertFails(
                () -> new IdeologyDef(new IdeologyId("democracy"), "Демократія", 100, List.of(), List.of(), List.of()),
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
                () -> new SubIdeologyDef(new SubIdeologyId("revanchism"), " ", 100, List.of(), List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new DoctrineDef(new DoctrineId("armored"), null, List.of(), List.of()), ErrorCode.BLANK_VALUE);
        assertFails(() -> new ResourceDef(new ResourceId("iron"), "", List.of()), ErrorCode.BLANK_VALUE);
    }

    @Test
    void developmentLevelIsWithinDesignRange() {
        assertThat(new DevelopmentLevelDef(-3, "Глибоке відставання", "Лише базові технології.", 8, 5, List.of())
                        .level())
                .isEqualTo(Development.MIN);
        assertFails(() -> level(-4, "Нижче дна", "—", 1, 0, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> level(3, "Понад лідера", "—", 1, 0, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> level(0, "Світовий рівень", " ", 1, 0, List.of()), ErrorCode.BLANK_VALUE);
    }

    @Test
    void developmentLevelWeightQualityAndTagsAreChecked() {
        DevelopmentLevelDef leader =
                level(2, "Лідер", "Попереду світу.", DevelopmentLevelDef.MAX_WEIGHT, 100, List.of("advanced"));
        assertThat(leader.weight()).isEqualTo(10_000);
        assertThat(leader.tags()).containsExactly("advanced");

        assertFails(() -> level(0, "Світовий рівень", "—", 0, 50, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> level(0, "Світовий рівень", "—", 10_001, 50, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> level(0, "Світовий рівень", "—", 1, -1, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> level(0, "Світовий рівень", "—", 1, 101, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> level(0, "Світовий рівень", "—", 1, 50, List.of("Advanced")), ErrorCode.INVALID_KEY_FORMAT);
        assertFails(
                () -> level(0, "Світовий рівень", "—", 1, 50, List.of("advanced", "advanced")), ErrorCode.DUPLICATE_ID);
    }

    private static DevelopmentLevelDef level(
            int level, String name, String description, int weight, int quality, List<String> tags) {
        return new DevelopmentLevelDef(level, name, description, weight, quality, tags);
    }

    @Test
    void branchAndNuclearStatusNeedNames() {
        assertFails(() -> new TechBranchDef(TechBranch.SOCIETY, ""), ErrorCode.BLANK_VALUE);
        assertFails(() -> new NuclearStatusDef(NuclearStatus.ARSENAL, null, 7, 90, List.of()), ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new NuclearStatusDef(NuclearStatus.ARSENAL, "Арсенал", 7, 90, List.of("NuclearPower")),
                ErrorCode.INVALID_KEY_FORMAT);
    }

    @Test
    void nuclearStatusWeightAndQualityAreBounded() {
        assertThat(new NuclearStatusDef(NuclearStatus.PROGRAM, "Програма", 13, 70, List.of("nuclear_program")))
                .extracting(NuclearStatusDef::weight, NuclearStatusDef::quality)
                .containsExactly(13, 70);
        assertFails(
                () -> new NuclearStatusDef(NuclearStatus.NONE, "Немає", 0, 50, List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new NuclearStatusDef(NuclearStatus.NONE, "Немає", NuclearStatusDef.MAX_WEIGHT + 1, 50, List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new NuclearStatusDef(NuclearStatus.NONE, "Немає", 80, 101, List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new NuclearStatusDef(NuclearStatus.NONE, "Немає", 80, -1, List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void gdpLevelFieldsAreBounded() {
        GdpLevelDef middle = gdp("middle", 1000, OutcomeTier.PARTIAL, 24, 50);
        assertThat(middle)
                .extracting(GdpLevelDef::perCapita, GdpLevelDef::tier, GdpLevelDef::weight, GdpLevelDef::quality)
                .containsExactly(1000, OutcomeTier.PARTIAL, 24, 50);
        assertFails(() -> gdp("middle", 0, OutcomeTier.PARTIAL, 24, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> gdp("middle", GdpLevelDef.MAX_PER_CAPITA + 1, OutcomeTier.PARTIAL, 24, 50),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> gdp("middle", 1000, OutcomeTier.PARTIAL, 0, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> gdp("middle", 1000, OutcomeTier.PARTIAL, GdpLevelDef.MAX_WEIGHT + 1, 50),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> gdp("middle", 1000, OutcomeTier.PARTIAL, 24, 101), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GdpLevelDef(
                        new GdpLevelId("middle"), " ", "Опис", 1000, OutcomeTier.PARTIAL, 24, 50, List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new GdpLevelDef(
                        new GdpLevelId("middle"),
                        "Середній",
                        "Опис",
                        1000,
                        OutcomeTier.PARTIAL,
                        24,
                        50,
                        List.of("Poor")),
                ErrorCode.INVALID_KEY_FORMAT);
        assertThatThrownBy(() ->
                        new GdpLevelDef(new GdpLevelId("middle"), "Середній", "Опис", 1000, null, 24, 50, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void gdpLevelFollowsPoorerLevelWithHigherIncomeAndNoLowerTier() {
        GdpLevelDef poor = gdp("poor", 250, OutcomeTier.FAIL, 10, 20);

        GdpLevelDef.checkFollows(poor, gdp("lower_middle", 500, OutcomeTier.FAIL, 10, 40));
        GdpLevelDef.checkFollows(poor, gdp("rich", 3500, OutcomeTier.CRIT_SUCCESS, 10, 80));
        assertThatThrownBy(() -> GdpLevelDef.checkFollows(poor, gdp("same", 250, OutcomeTier.SUCCESS, 10, 50)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details())
                            .containsExactly(entry("field", "gdp_level.same.per_capita"), entry("value", 250));
                });
        assertThatThrownBy(() -> GdpLevelDef.checkFollows(poor, gdp("worse", 500, OutcomeTier.CRIT_FAIL, 10, 5)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details())
                            .containsExactly(entry("field", "gdp_level.worse.tier"), entry("value", "crit_fail"));
                });
    }

    @Test
    void hdiLevelFieldsAreBoundedByHdiStat() {
        HdiLevelDef middle = hdi("middle", 60, OutcomeTier.PARTIAL, 24, 50);
        assertThat(middle)
                .extracting(HdiLevelDef::hdi, HdiLevelDef::tier, HdiLevelDef::weight, HdiLevelDef::quality)
                .containsExactly(60, OutcomeTier.PARTIAL, 24, 50);
        assertThat(hdi("lowest", 0, OutcomeTier.CRIT_FAIL, 1, 0).hdi()).isZero();
        assertThat(hdi("highest", 100, OutcomeTier.CRIT_SUCCESS, HdiLevelDef.MAX_WEIGHT, 100)
                        .hdi())
                .isEqualTo(100);
        assertFails(() -> hdi("middle", -1, OutcomeTier.PARTIAL, 24, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> hdi("middle", 101, OutcomeTier.PARTIAL, 24, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> hdi("middle", 60, OutcomeTier.PARTIAL, 0, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> hdi("middle", 60, OutcomeTier.PARTIAL, HdiLevelDef.MAX_WEIGHT + 1, 50),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> hdi("middle", 60, OutcomeTier.PARTIAL, 24, 101), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new HdiLevelDef(
                        new HdiLevelId("middle"), "Середній", " ", 60, OutcomeTier.PARTIAL, 24, 50, List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new HdiLevelDef(
                        new HdiLevelId("middle"),
                        "Середній",
                        "Опис",
                        60,
                        OutcomeTier.PARTIAL,
                        24,
                        50,
                        List.of("Educated")),
                ErrorCode.INVALID_KEY_FORMAT);
        assertThatThrownBy(() ->
                        new HdiLevelDef(new HdiLevelId("middle"), "Середній", "Опис", 60, null, 24, 50, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void hdiLevelFollowsLowerLevelWithHigherHdiAndNoLowerTier() {
        HdiLevelDef low = hdi("low", 30, OutcomeTier.FAIL, 10, 20);

        HdiLevelDef.checkFollows(low, hdi("lower_middle", 45, OutcomeTier.FAIL, 10, 40));
        HdiLevelDef.checkFollows(low, hdi("high", 75, OutcomeTier.CRIT_SUCCESS, 10, 80));
        assertThatThrownBy(() -> HdiLevelDef.checkFollows(low, hdi("same", 30, OutcomeTier.SUCCESS, 10, 50)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details()).containsExactly(entry("field", "hdi_level.same.hdi"), entry("value", 30));
                });
        assertThatThrownBy(() -> HdiLevelDef.checkFollows(low, hdi("worse", 45, OutcomeTier.CRIT_FAIL, 10, 5)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details())
                            .containsExactly(entry("field", "hdi_level.worse.tier"), entry("value", "crit_fail"));
                });
    }

    @Test
    void armySizeShareIsAPartOfPopulation() {
        ArmySizeDef regular = army("regular", 150, OutcomeTier.PARTIAL, 28, 50);
        assertThat(regular)
                .extracting(ArmySizeDef::shareBp, ArmySizeDef::tier, ArmySizeDef::weight, ArmySizeDef::quality)
                .containsExactly(150, OutcomeTier.PARTIAL, 28, 50);
        assertThat(army("tiny", 1, OutcomeTier.CRIT_FAIL, 1, 0).shareBp()).isEqualTo(1);
        assertThat(army("everyone", ArmySizeDef.MAX_SHARE_BP, OutcomeTier.CRIT_SUCCESS, ArmySizeDef.MAX_WEIGHT, 100)
                        .shareBp())
                .isEqualTo(ArmySizeDef.MAX_SHARE_BP);
        assertFails(() -> army("regular", 0, OutcomeTier.PARTIAL, 28, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> army("regular", ArmySizeDef.MAX_SHARE_BP + 1, OutcomeTier.PARTIAL, 28, 50),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> army("regular", 150, OutcomeTier.PARTIAL, 0, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> army("regular", 150, OutcomeTier.PARTIAL, ArmySizeDef.MAX_WEIGHT + 1, 50),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> army("regular", 150, OutcomeTier.PARTIAL, 28, 101), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ArmySizeDef(
                        new ArmySizeId("regular"), " ", "Опис", 150, OutcomeTier.PARTIAL, 28, 50, List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new ArmySizeDef(
                        new ArmySizeId("regular"),
                        "Звичайна",
                        "Опис",
                        150,
                        OutcomeTier.PARTIAL,
                        28,
                        50,
                        List.of("Large Army")),
                ErrorCode.INVALID_KEY_FORMAT);
        assertThatThrownBy(() ->
                        new ArmySizeDef(new ArmySizeId("regular"), "Звичайна", "Опис", 150, null, 28, 50, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void armySizeFollowsSmallerArmyWithLargerShareAndNoLowerTier() {
        ArmySizeDef small = army("small", 40, OutcomeTier.FAIL, 10, 30);

        ArmySizeDef.checkFollows(small, army("modest", 80, OutcomeTier.FAIL, 10, 40));
        ArmySizeDef.checkFollows(small, army("mass", 500, OutcomeTier.CRIT_SUCCESS, 10, 80));
        assertThatThrownBy(() -> ArmySizeDef.checkFollows(small, army("same", 40, OutcomeTier.SUCCESS, 10, 50)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details())
                            .containsExactly(entry("field", "army_size.same.share_bp"), entry("value", 40));
                });
        assertThatThrownBy(() -> ArmySizeDef.checkFollows(small, army("worse", 80, OutcomeTier.CRIT_FAIL, 10, 5)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details())
                            .containsExactly(entry("field", "army_size.worse.tier"), entry("value", "crit_fail"));
                });
    }

    @Test
    void personKindNeedsNameAndDescription() {
        assertThat(new PersonKindDef(PersonKind.GENERAL, "Генерал", "Командує фронтом.", List.of("military")).tags())
                .containsExactly("military");
        assertFails(
                () -> new PersonKindDef(PersonKind.GENERAL, " ", "Командує фронтом.", List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(() -> new PersonKindDef(PersonKind.GENERAL, "Генерал", "", List.of()), ErrorCode.BLANK_VALUE);
    }

    @Test
    void traitWithoutKindsSuitsEveryone() {
        TraitDef charismatic = TestContent.trait("charismatic", List.of());
        TraitDef genius = TestContent.trait("genius", List.of(PersonKind.SCIENTIST));

        for (PersonKind kind : PersonKind.values()) {
            assertThat(charismatic.allows(kind)).isTrue();
            assertThat(genius.allows(kind)).isEqualTo(kind == PersonKind.SCIENTIST);
        }
    }

    @Test
    void traitRejectsRepeatsAndSelfIncompatibility() {
        assertFails(() -> TestContent.trait("loyal", List.of(), "loyal"), ErrorCode.SELF_REFERENCE);
        assertFails(() -> TestContent.trait("loyal", List.of(), "corrupt", "corrupt"), ErrorCode.DUPLICATE_ID);
        assertThatThrownBy(() -> TestContent.trait("genius", List.of(PersonKind.SCIENTIST, PersonKind.SCIENTIST)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details())
                            .containsExactly(entry("field", "trait.genius.kinds"), entry("value", "scientist"));
                });
        assertFails(
                () -> new TraitDef(new TraitId("genius"), "", List.of(), List.of(), List.of(), List.of()),
                ErrorCode.BLANK_VALUE);
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

    private static ArmySizeDef army(String id, int shareBp, OutcomeTier tier, int weight, int quality) {
        return new ArmySizeDef(new ArmySizeId(id), "Рівень", "Опис", shareBp, tier, weight, quality, List.of());
    }

    private static HdiLevelDef hdi(String id, int hdi, OutcomeTier tier, int weight, int quality) {
        return new HdiLevelDef(new HdiLevelId(id), "Рівень", "Опис", hdi, tier, weight, quality, List.of());
    }

    private static GdpLevelDef gdp(String id, int perCapita, OutcomeTier tier, int weight, int quality) {
        return new GdpLevelDef(new GdpLevelId(id), "Рівень", "Опис", perCapita, tier, weight, quality, List.of());
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
