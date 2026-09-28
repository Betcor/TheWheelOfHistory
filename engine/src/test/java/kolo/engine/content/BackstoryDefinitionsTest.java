package kolo.engine.content;

import static kolo.engine.content.TestContent.fragment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class BackstoryDefinitionsTest {

    private static final LocalizedName VELOR = new LocalizedName(
            new NounPhrase(
                    GrammaticalGender.FEMININE,
                    List.of(
                            "Республіка Велор",
                            "Республіки Велор",
                            "Республіці Велор",
                            "Республіку Велор",
                            "Республікою Велор",
                            "Республіці Велор",
                            "Республіко Велор")),
            new NounPhrase(
                    GrammaticalGender.MASCULINE,
                    List.of("Велор", "Велору", "Велору", "Велор", "Велором", "Велорі", "Велоре")));
    private static final LocalizedName GARIA = new LocalizedName(
            new NounPhrase(
                    GrammaticalGender.NEUTER,
                    List.of(
                            "Королівство Гарія",
                            "Королівства Гарія",
                            "Королівству Гарія",
                            "Королівство Гарія",
                            "Королівством Гарія",
                            "Королівстві Гарія",
                            "Королівство Гарія")),
            new NounPhrase(
                    GrammaticalGender.FEMININE,
                    List.of("Гарія", "Гарії", "Гарії", "Гарію", "Гарією", "Гарії", "Гаріє")));

    // ---- Шаблон тексту ----

    @Test
    void textRendersYearNamesAndCases() {
        BackstoryText text = new BackstoryText(
                "text",
                "{country} у {year} році: війна з {neighbor.instrumental}, мир із {neighbor_full.instrumental}");

        assertThat(text.usesYear()).isTrue();
        assertThat(text.usesNeighbor()).isTrue();
        assertThat(text.render(VELOR, Optional.of(GARIA), 1958))
                .isEqualTo("Велор у 1958 році: війна з Гарією, мир із Королівством Гарія");
        assertThat(new BackstoryText("text", "Заводи {country_full.genitive} та {country_full}.")
                        .render(VELOR, Optional.empty(), 1960))
                .isEqualTo("Заводи Республіки Велор та Республіка Велор.");
        assertThat(new BackstoryText("text", "Жодних змінних.").usesYear()).isFalse();
    }

    @Test
    void textWithoutNeighborIgnoresGivenNeighborButNeedsOneWhenMentioned() {
        BackstoryText own = new BackstoryText("text", "У {country.locative} мир.");
        BackstoryText feud = new BackstoryText("text", "Ворожнеча з {neighbor.instrumental}.");

        assertThat(own.usesNeighbor()).isFalse();
        assertThat(own.render(VELOR, Optional.of(GARIA), 1950)).isEqualTo("У Велорі мир.");
        assertThatThrownBy(() -> feud.render(VELOR, Optional.empty(), 1950))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.BLANK_VALUE);
                    assertThat(e.details()).containsExactly(entry("field", "neighbor"));
                });
    }

    @Test
    void malformedTextIsRejected() {
        assertInvalidTemplate("Війна з {enemy}.", "{enemy}");
        assertInvalidTemplate("Війна з {neighbor.ablative}.", "{neighbor.ablative}");
        assertInvalidTemplate("Війна з {neighbor.}.", "{neighbor.}");
        assertInvalidTemplate("Рік {year", "Рік {year");
        assertInvalidTemplate("Рік year}", "Рік year}");
        assertInvalidTemplate("Рік {{year}}", "{{year}");
        assertInvalidTemplate(" Рік {year}", " Рік {year}");
        assertFails(() -> new BackstoryText("text", " "), ErrorCode.BLANK_VALUE);
        assertFails(() -> new BackstoryText("text", null), ErrorCode.BLANK_VALUE);
    }

    // ---- Умова на мітки ----

    @Test
    void conditionNeedsAllRequiredAnyOfAlternativesAndNoExcluded() {
        TagCondition condition =
                new TagCondition(List.of("lost_war"), List.of("revanchism", "militarism"), List.of("nuclear_power"));

        assertThat(condition.matches(Set.of("lost_war", "militarism"))).isTrue();
        assertThat(condition.matches(Set.of("lost_war")))
                .as("жодної з альтернатив")
                .isFalse();
        assertThat(condition.matches(Set.of("revanchism")))
                .as("бракує обов'язкової")
                .isFalse();
        assertThat(condition.matches(Set.of("lost_war", "revanchism", "nuclear_power")))
                .as("є виключна")
                .isFalse();
        assertThat(TagCondition.NONE.matches(Set.of())).isTrue();
        assertThat(condition.tags()).containsExactly("lost_war", "revanchism", "militarism", "nuclear_power");
    }

    @Test
    void conditionTagAppearsInOneListOnly() {
        assertThatThrownBy(() -> new TagCondition(List.of("lost_war"), List.of(), List.of("lost_war")))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details()).containsExactly(entry("field", "condition"), entry("value", "lost_war"));
                });
        assertFails(() -> new TagCondition(List.of("a", "a"), List.of(), List.of()), ErrorCode.DUPLICATE_ID);
        assertFails(() -> new TagCondition(List.of(), List.of("Lost"), List.of()), ErrorCode.INVALID_KEY_FORMAT);
    }

    // ---- Фрагмент ----

    @Test
    void fragmentWeightIsBasePlusBonusesClampedToRange() {
        BackstoryFragmentDef lostWar = withWeights(100, Map.of("revanchism", 300, "democratic", -250));

        assertThat(lostWar.weightFor(Set.of())).isEqualTo(100);
        assertThat(lostWar.weightFor(Set.of("revanchism", "unrelated"))).isEqualTo(400);
        assertThat(lostWar.weightFor(Set.of("revanchism", "democratic"))).isEqualTo(150);
        assertThat(lostWar.weightFor(Set.of("democratic"))).as("не менше нуля").isZero();
        assertThat(withWeights(9_000, Map.of("a", 5_000)).weightFor(Set.of("a")))
                .isEqualTo(BackstoryFragmentDef.MAX_WEIGHT);
        assertThat(lostWar.referencedTags()).containsExactly("democratic", "revanchism");
    }

    @Test
    void neighborFragmentIsAvailableOnlyWithNeighbor() {
        BackstoryFragmentDef feud = feud(true, "Ворожнеча з {neighbor.instrumental}.");
        BackstoryFragmentDef own =
                fragment("famine", new TagCondition(List.of(), List.of(), List.of("rich")), List.of());

        assertThat(feud.available(Set.of(), true)).isTrue();
        assertThat(feud.available(Set.of(), false)).isFalse();
        assertThat(own.available(Set.of(), false)).isTrue();
        assertThat(own.available(Set.of("rich"), true)).isFalse();
    }

    @Test
    void neighborFlagMustMatchText() {
        assertThatThrownBy(() -> feud(true, "Ворожнеча без сусіда."))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INVALID_TEMPLATE);
                    assertThat(e.details()).contains(entry("field", "backstory.old_feud.text"));
                });
        assertFails(() -> feud(false, "Ворожнеча з {neighbor.instrumental}."), ErrorCode.INVALID_TEMPLATE);
    }

    @Test
    void fragmentNumbersAreWithinDesignRanges() {
        assertFails(() -> build(0, 50, 1950, 1960, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> build(10_001, 50, 1950, 1960, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> build(100, 101, 1950, 1960, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> build(100, -1, 1950, 1960, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> build(100, 50, 1899, 1960, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> build(100, 50, 1950, 1970, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> build(100, 50, 1960, 1950, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> build(100, 50, 1950, 1960, -1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> build(100, 50, 1950, 1960, 101), ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(build(1, 0, 1900, 1969, 100).yearTo()).isEqualTo(BackstoryFragmentDef.LATEST_YEAR);
        assertThat(build(100, 100, 1969, 1969, 0).yearFrom()).isEqualTo(1969);
    }

    @Test
    void weightBonusesAreValidated() {
        assertFails(() -> withWeights(100, Map.of("Revanchism", 10)), ErrorCode.INVALID_KEY_FORMAT);
        assertFails(() -> withWeights(100, Map.of("a", 10_001)), ErrorCode.VALUE_OUT_OF_RANGE);
        TreeMap<String, Integer> withNull = new TreeMap<>();
        withNull.put("revanchism", null);
        assertFails(() -> withWeights(100, withNull), ErrorCode.BLANK_VALUE);
        assertFails(() -> fragment("x", TagCondition.NONE, List.of("lost_war", "lost_war")), ErrorCode.DUPLICATE_ID);
    }

    // ---- Набір передісторії ----

    @Test
    void contentIndexesFragmentsAndCollectsProducedTags() {
        BackstoryContent content = new BackstoryContent(
                Map.of("poor", "Бідна країна."),
                List.of(
                        fragment("reparations", new TagCondition(List.of("lost_war"), List.of(), List.of()), List.of()),
                        fragment("lost_war", TagCondition.NONE, List.of("lost_war", "lost_territories"))));

        assertThat(content.fragments().keySet())
                .extracting(BackstoryFragmentId::value)
                .containsExactly("lost_war", "reparations");
        assertThat(content.generationTags()).containsExactly(entry("poor", "Бідна країна."));
        assertThat(content.producedTags()).containsExactly("lost_territories", "lost_war", "poor");
        assertThat(content.available(Set.of(), false))
                .extracting(fragment -> fragment.id().value())
                .containsExactly("lost_war");
        assertThat(content.available(Set.of("lost_war"), false)).hasSize(2);
        assertThat(content.fragment(new BackstoryFragmentId("lost_war"))).isPresent();
        assertThat(content.fragment(new BackstoryFragmentId("famine"))).isEmpty();
    }

    @Test
    void contentRejectsEmptyDuplicateAndBadGenerationTags() {
        BackstoryFragmentDef famine = fragment("famine", TagCondition.NONE, List.of());

        assertFails(() -> new BackstoryContent(Map.of(), List.of()), ErrorCode.EMPTY_COLLECTION);
        assertFails(() -> new BackstoryContent(Map.of(), List.of(famine, famine)), ErrorCode.DUPLICATE_ID);
        assertFails(
                () -> new BackstoryContent(Map.of("Poor", "Бідна."), List.of(famine)), ErrorCode.INVALID_KEY_FORMAT);
        assertFails(() -> new BackstoryContent(Map.of("poor", " "), List.of(famine)), ErrorCode.BLANK_VALUE);
    }

    private static BackstoryFragmentDef build(int weight, int quality, int from, int to, int duration) {
        return new BackstoryFragmentDef(
                new BackstoryFragmentId("civil_war"),
                weight,
                quality,
                from,
                to,
                TagCondition.NONE,
                new TreeMap<>(),
                false,
                List.of(),
                duration,
                List.of(),
                new BackstoryText("text", "Громадянська війна {year} року."));
    }

    private static BackstoryFragmentDef withWeights(int weight, Map<String, Integer> bonuses) {
        return new BackstoryFragmentDef(
                new BackstoryFragmentId("lost_war"),
                weight,
                10,
                1945,
                1966,
                TagCondition.NONE,
                new TreeMap<>(bonuses),
                false,
                List.of("lost_war"),
                10,
                List.of(),
                new BackstoryText("text", "Поразка {year} року."));
    }

    private static BackstoryFragmentDef feud(boolean neighbor, String text) {
        return new BackstoryFragmentDef(
                new BackstoryFragmentId("old_feud"),
                60,
                30,
                1900,
                1960,
                TagCondition.NONE,
                new TreeMap<>(),
                neighbor,
                List.of("old_feud"),
                0,
                List.of(),
                new BackstoryText("text", text));
    }

    private static void assertInvalidTemplate(String template, String value) {
        assertThatThrownBy(() -> new BackstoryText("backstory.x.text", template))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INVALID_TEMPLATE);
                    assertThat(e.details()).containsExactly(entry("field", "backstory.x.text"), entry("value", value));
                });
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
