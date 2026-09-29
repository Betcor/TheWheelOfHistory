package kolo.content.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kolo.engine.content.ArchetypeDef;
import kolo.engine.content.ArchetypeId;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.BackstoryContent;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.BalanceDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.DogmaDef;
import kolo.engine.content.DogmaId;
import kolo.engine.content.FaithFormDef;
import kolo.engine.content.FaithFormId;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.HdiLevelDef;
import kolo.engine.content.HdiLevelId;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.MapTemplateId;
import kolo.engine.content.MedianRange;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameParadigmId;
import kolo.engine.content.NameStyleDef;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.PersonNameStyleDef;
import kolo.engine.content.ReligionBalanceDef;
import kolo.engine.content.ReligionContent;
import kolo.engine.content.ReligionCountDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.SecularStateDef;
import kolo.engine.content.StateReligionDef;
import kolo.engine.content.StepRange;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.content.StreakRewardId;
import kolo.engine.content.StreakRulesDef;
import kolo.engine.content.StreakWheelDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.SurnameFinalDef;
import kolo.engine.content.TagCondition;
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.content.WorldBalanceDef;
import kolo.engine.error.ContentException;
import kolo.engine.error.ErrorCode;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.NpcShare;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.Sex;
import kolo.engine.state.Stat;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

class ContentLoaderTest {

    @Test
    void loadsValidContentIntoModel() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        IdeologyDef democracy = pack.ideology(new IdeologyId("democracy")).orElseThrow();
        assertThat(democracy.name()).isEqualTo("Демократія");
        assertThat(democracy.tags()).containsExactly("democratic");
        assertThat(democracy.modifiers()).containsExactly(new ModifierDef(ModifierTarget.stat(Stat.HDI), 5));
        assertThat(democracy.subIdeologies().getFirst().modifiers())
                .containsExactly(new ModifierDef(ModifierTarget.wheel(new WheelKind("economic_cycle")), 10));
        assertThat(pack.ideologyOf(new SubIdeologyId("revanchism")))
                .map(IdeologyDef::id)
                .contains(new IdeologyId("totalitarianism"));
        // Відсутні списки — порожні.
        assertThat(pack.resource(new ResourceId("iron")).orElseThrow().tags()).isEmpty();
        assertThat(pack.hash()).matches("[0-9a-f]{64}");
    }

    @Test
    void loadsDevelopmentAndNuclearStatuses() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        assertThat(pack.techBranch(TechBranch.ENERGY_SCIENCE).name()).isEqualTo("Енергетика й наука");
        assertThat(pack.developmentLevel(-3).name()).isEqualTo("Глибоке відставання");
        assertThat(pack.developmentLevel(-3).weight()).isEqualTo(8);
        assertThat(pack.developmentLevel(-3).quality()).isEqualTo(5);
        assertThat(pack.developmentLevel(-3).tags()).containsExactly("backward");
        assertThat(pack.developmentLevel(0).tags()).isEmpty();
        assertThat(pack.nuclearStatus(NuclearStatus.ARSENAL).tags()).containsExactly("nuclear_power");
        assertThat(pack.nuclearStatus(NuclearStatus.NONE).tags()).isEmpty();
        assertThat(pack.nuclearStatus(NuclearStatus.PROGRAM).weight()).isEqualTo(13);
        assertThat(pack.nuclearStatus(NuclearStatus.ARSENAL).quality()).isEqualTo(90);
    }

    @Test
    void missingNuclearWeightOrQualityIsAnErrorNotZero() {
        assertContentError(
                Files.valid()
                        .with(ContentLoader.NUCLEAR, Files.NUCLEAR.replace("    weight: 13\n", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "file", "nuclear.yaml",
                        "location", "statuses[1]",
                        "cause", "blank_value",
                        "field", "weight"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.NUCLEAR, Files.NUCLEAR.replace("    quality: 90\n", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "statuses[2]", "cause", "blank_value", "field", "quality"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.NUCLEAR, Files.NUCLEAR.replace("weight: 7", "weight: 0"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "statuses[2]", "cause", "value_out_of_range"));
    }

    @Test
    void loadsPersonKindsAndTraits() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        assertThat(pack.personKind(PersonKind.GENERAL).tags()).containsExactly("military");
        assertThat(pack.personKind(PersonKind.GENERAL).weight()).isEqualTo(10);
        assertThat(pack.personKind(PersonKind.GENERAL).weightTags()).containsExactly(entry("educated", 5));
        assertThat(pack.personKind(PersonKind.ARTIST).weightTags()).isEmpty();
        assertThat(pack.personKind(PersonKind.PRETENDER).name()).isEqualTo("Диктатор-претендент");
        TraitDef genius = pack.trait(new TraitId("genius")).orElseThrow();
        assertThat(genius.kinds()).containsExactly(PersonKind.SCIENTIST);
        assertThat(genius.tags()).isEmpty();
        assertThat(pack.trait(new TraitId("loyal")).orElseThrow().kinds())
                .as("без kinds — будь-який тип")
                .isEmpty();
        assertThat(pack.compatible(new TraitId("treacherous"), new TraitId("loyal")))
                .isFalse();
    }

    @Test
    void invalidPeopleContentIsReportedAtItsPosition() {
        assertContentError(
                Files.valid()
                        .with(ContentLoader.PEOPLE, Files.PEOPLE.replace("kinds: [scientist]", "kinds: [wizard]"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "file", "people.yaml",
                        "location", "traits[2].kinds[0]",
                        "cause", "unknown_reference",
                        "field", "trait.kinds",
                        "value", "wizard"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.PEOPLE, Files.PEOPLE.replace("[treacherous]", "[coward]"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "traits[0].incompatible[0]", "cause", "unknown_reference", "value", "coward"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.PEOPLE, Files.PEOPLE.replace("[treacherous]", "[loyal]"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "traits[0]", "cause", "self_reference"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.PEOPLE, Files.PEOPLE.replaceAll("(?m)^  - \\{ id: artist,.*\n", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "kinds", "cause", "missing_definition", "value", "artist"));
    }

    @Test
    void personKindWeightIsRequiredAndInRange() {
        assertContentError(
                Files.valid()
                        .with(
                                ContentLoader.PEOPLE,
                                Files.PEOPLE.replace(
                                        "description: Формує культуру., weight: 10", "description: Формує культуру."))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("file", "people.yaml", "location", "kinds[7]", "cause", "blank_value", "field", "weight"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.PEOPLE, Files.PEOPLE.replace("{ educated: 5 }", "{ educated: 10001 }"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "kinds[1]", "cause", "value_out_of_range"));
    }

    @Test
    void personKindWeightTagsNeedASource() {
        assertContentError(
                Files.valid()
                        .with(ContentLoader.PEOPLE, Files.PEOPLE.replace("{ educated: 5 }", "{ wizardry: 5 }"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "file", "people.yaml",
                        "location", "kinds[1].weight_tags",
                        "cause", "unknown_reference",
                        "value", "wizardry"));
        // Мітка рівня ІЛР — джерело; без неї посилання повисає.
        assertContentError(
                Files.valid()
                        .with(ContentLoader.HDI, Files.HDI.replace(", tags: [educated]", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("file", "people.yaml", "location", "kinds[1].weight_tags", "value", "educated"));
    }

    @Test
    void everyPersonKindNeedsSomeTrait() {
        String onlyScientists = Files.PEOPLE.substring(0, Files.PEOPLE.indexOf("traits:"))
                + "traits:\n  - { id: genius, name: Геній, kinds: [scientist] }\n";

        assertContentError(
                Files.valid().with(ContentLoader.PEOPLE, onlyScientists).source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("file", "people.yaml", "location", "traits", "cause", "missing_definition", "value", "general"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.PEOPLE, Files.PEOPLE.substring(0, Files.PEOPLE.indexOf("traits:")))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "traits", "cause", "empty_collection"));
    }

    @Test
    void loadsNames() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        NameStyleDef northern = pack.names().style(new NameStyleId("northern")).orElseThrow();
        assertThat(northern.starts()).containsExactly("вел", "тор");
        assertThat(northern.middleChanceBp()).isEqualTo(3000);
        assertThat(pack.names().paradigm(northern.finals().getFirst()).gender()).isEqualTo(GrammaticalGender.MASCULINE);
        assertThat(pack.names().paradigm(northern.finals().getFirst()).ending(GrammaticalCase.NOMINATIVE))
                .isEmpty();
        assertThat(pack.stateFormsFor(new SubIdeologyId("revanchism")))
                .extracting(form -> form.id().value())
                .containsExactly("state");
        assertThat(pack.stateFormsFor(new SubIdeologyId("liberal_democracy"))
                        .getFirst()
                        .render(GrammaticalCase.GENITIVE, "Велор"))
                .isEqualTo("Республіки Велор");
    }

    @Test
    void invalidNamesAreReportedAtTheirPosition() {
        assertNamesError(
                Files.NAMES.replace("paradigm: masc_hard", "paradigm: masc_soft"),
                Map.of(
                        "file", "names.yaml",
                        "location", "styles[0].finals[0].paradigm",
                        "cause", "unknown_reference",
                        "value", "masc_soft"));
        assertNamesError(
                Files.NAMES.replace("ideologies: [democracy]", "ideologies: [theocracy]"),
                Map.of("location", "state_forms[0].ideologies[0]", "cause", "unknown_reference", "value", "theocracy"));
        assertNamesError(
                Files.NAMES.replace("sub_ideologies: [revanchism]", "sub_ideologies: [militarism]"),
                Map.of(
                        "location",
                        "state_forms[1].sub_ideologies[0]",
                        "cause",
                        "unknown_reference",
                        "value",
                        "militarism"));
        assertNamesError(
                Files.NAMES.replace("sub_ideologies: [revanchism]", "ideologies: [democracy]"),
                Map.of("location", "state_forms", "cause", "missing_definition", "value", "revanchism"));
        assertNamesError(
                Files.NAMES.replace("starts: [вел, тор]", "starts: [веле, тор]"),
                Map.of("location", "styles[0]", "cause", "invalid_name_format", "value", "веле"));
        assertNamesError(
                Files.NAMES.replace("      vocative: Державо {root}\n", ""),
                Map.of(
                        "location",
                        "state_forms[1]",
                        "cause",
                        "blank_value",
                        "field",
                        "state_form.state.templates.vocative"));
        assertNamesError(
                Files.NAMES.replace("gender: masculine", "gender: male"),
                Map.of("location", "paradigms[0]", "cause", "unknown_reference", "value", "male"));
        assertNamesError(
                Files.NAMES.replaceAll("(?m)^    endings: .*\n", ""),
                Map.of("location", "paradigms[0]", "cause", "blank_value", "field", "endings"));
    }

    @Test
    void loadsPersonNames() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        PersonNameStyleDef northern =
                pack.names().personStyle(new NameStyleId("northern")).orElseThrow();
        assertThat(northern.given().starts()).containsExactly("ал", "дар");
        assertThat(northern.given().middleChanceBp()).isEqualTo(1000);
        assertThat(northern.givenFinals(Sex.MALE))
                .containsExactly(new NameFinalDef("ор", new NameParadigmId("person_masc")));
        assertThat(northern.givenFinals(Sex.FEMALE))
                .containsExactly(new NameFinalDef("ін", new NameParadigmId("fem_hard")));
        assertThat(northern.surnames().middles()).isEmpty();
        assertThat(northern.surnameFinals())
                .containsExactly(
                        new SurnameFinalDef("ер", new NameParadigmId("person_masc"), new NameParadigmId("fixed_fem")));
    }

    @Test
    void invalidPersonNamesAreReportedAtTheirPosition() {
        assertNamesError(
                Files.NAMES.replace("{ text: ор, paradigm: person_masc }", "{ text: ор, paradigm: fem_hard }"),
                Map.of(
                        "file", "names.yaml",
                        "location", "person_styles[0].given_names.male[0].paradigm",
                        "cause", "name_gender_mismatch",
                        "value", "fem_hard",
                        "expected", "masculine"));
        assertNamesError(
                Files.NAMES.replace("female: fixed_fem", "female: person_masc"),
                Map.of(
                        "location",
                        "person_styles[0].surnames.finals[0].female",
                        "cause",
                        "name_gender_mismatch",
                        "expected",
                        "feminine"));
        assertNamesError(
                Files.NAMES.replace("male: person_masc,", "male: masc_soft,"),
                Map.of(
                        "location",
                        "person_styles[0].surnames.finals[0].male",
                        "cause",
                        "unknown_reference",
                        "value",
                        "masc_soft"));
        assertNamesError(
                Files.NAMES.replace("person_styles:\n  - id: northern", "person_styles:\n  - id: southern"),
                Map.of("location", "person_styles[0].id", "cause", "unknown_reference", "value", "southern"));
        assertNamesError(
                Files.NAMES.substring(0, Files.NAMES.indexOf("person_styles:")),
                Map.of("location", "person_styles", "cause", "empty_collection"));
        assertNamesError(
                Files.NAMES.replace("starts: [ал, дар]", "starts: [але, дар]"),
                Map.of("location", "person_styles[0].given_names", "cause", "invalid_name_format", "value", "але"));
        assertNamesError(
                Files.NAMES.replaceAll("(?m)^ *- \\{ text: ін, paradigm: fem_hard }\n", ""),
                Map.of(
                        "location",
                        "person_styles[0]",
                        "cause",
                        "empty_collection",
                        "field",
                        "person_name_style.northern.female_finals"));
        assertNamesError(
                Files.NAMES.replaceAll("(?s)    given_names:.*?    surnames:", "    surnames:"),
                Map.of("location", "person_styles[0].given_names", "cause", "blank_value"));
        // Стиль назв без стилю імен: людей такої держави не буде як назвати.
        assertNamesError(
                Files.NAMES.replaceFirst("(?m)^styles:\n", """
                        styles:
                          - id: southern
                            name: Південний
                            middle_chance_bp: 0
                            starts: [сал]
                            finals:
                              - { text: ан, paradigm: masc_hard }
                        """),
                Map.of("location", "person_styles", "cause", "missing_definition", "value", "southern"));
    }

    @Test
    void loadsBackstory() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        BackstoryContent backstory = pack.backstory();
        assertThat(backstory.generationTags()).containsExactly(entry("large_army", "Велика армія."));
        BackstoryFragmentDef lostWar =
                backstory.fragment(new BackstoryFragmentId("lost_war")).orElseThrow();
        assertThat(lostWar.weight()).isEqualTo(80);
        assertThat(lostWar.quality()).isEqualTo(10);
        assertThat(lostWar.yearFrom()).isEqualTo(1945);
        assertThat(lostWar.yearTo()).isEqualTo(1966);
        assertThat(lostWar.neighbor()).isTrue();
        assertThat(lostWar.weightTags()).containsExactly(entry("large_army", 100), entry("revanchism", 400));
        assertThat(lostWar.adds()).containsExactly("lost_war");
        assertThat(lostWar.durationYears()).isEqualTo(10);
        assertThat(lostWar.modifiers()).containsExactly(new ModifierDef(ModifierTarget.stat(Stat.STABILITY), -5));
        assertThat(lostWar.text().template()).isEqualTo("У {year} році країна програла війну {neighbor.dative}.");

        BackstoryFragmentDef reparations =
                backstory.fragment(new BackstoryFragmentId("reparations")).orElseThrow();
        assertThat(reparations.condition())
                .isEqualTo(new TagCondition(
                        List.of("lost_war"), List.of("revanchism", "democratic"), List.of("nuclear_power")));
        // Без полів: без сусіда, модифікатори постійні, добавок до ваги немає.
        assertThat(reparations.neighbor()).isFalse();
        assertThat(reparations.durationYears()).isZero();
        assertThat(reparations.weightTags()).isEmpty();
    }

    @Test
    void invalidBackstoryIsReportedAtItsPosition() {
        assertBackstoryError(
                Files.BACKSTORY.replace("requires: [lost_war]", "requires: [lost_wars]"),
                Map.of(
                        "file", "backstory.yaml",
                        "location", "fragments[1]",
                        "cause", "unknown_reference",
                        "value", "lost_wars"));
        assertBackstoryError(
                Files.BACKSTORY.replace("{neighbor.dative}", "{neighbour.dative}"),
                Map.of("location", "fragments[0]", "cause", "invalid_template", "value", "{neighbour.dative}"));
        assertBackstoryError(
                Files.BACKSTORY.replace("    neighbor: true\n", ""),
                Map.of("location", "fragments[0]", "cause", "invalid_template", "field", "backstory.lost_war.text"));
        assertBackstoryError(
                Files.BACKSTORY.replace("    quality: 5\n", ""),
                Map.of("location", "fragments[1]", "cause", "blank_value", "field", "quality"));
        assertBackstoryError(
                Files.BACKSTORY.replace("    years: { from: 1946, to: 1968 }\n", ""),
                Map.of("location", "fragments[1]", "cause", "blank_value", "field", "years"));
        assertBackstoryError(
                Files.BACKSTORY.replace("to: 1968", "to: 1970"),
                Map.of(
                        "location",
                        "fragments[1]",
                        "cause",
                        "value_out_of_range",
                        "field",
                        "backstory.reparations.years.to"));
        assertBackstoryError(
                Files.BACKSTORY.replace("excludes: [nuclear_power]", "excludes: [lost_war]"),
                Map.of("location", "fragments[1]", "cause", "duplicate_id", "value", "lost_war"));
        assertBackstoryError(
                Files.BACKSTORY.replace("id: reparations", "id: lost_war"),
                Map.of("location", "fragments[1]", "cause", "duplicate_id", "value", "lost_war"));
        assertBackstoryError(
                Files.BACKSTORY.replace("value: -5", "value: 500"),
                Map.of("location", "fragments[0].modifiers[0]", "cause", "value_out_of_range"));
        assertBackstoryError(
                Files.BACKSTORY.replace("large_army: Велика армія.", "large_army: \"\""),
                Map.of("location", "", "cause", "blank_value", "field", "generation_tags.large_army"));
        assertBackstoryError(
                Files.BACKSTORY.substring(0, Files.BACKSTORY.indexOf("fragments:")),
                Map.of("location", "fragments", "cause", "empty_collection"));
    }

    @Test
    void backstoryFlagsAndNumbersAreStrict() {
        assertContentError(
                Files.valid()
                        .with(ContentLoader.BACKSTORY, Files.BACKSTORY.replace("neighbor: true", "neighbor: \"yes\""))
                        .source(),
                ErrorCode.CONTENT_MALFORMED,
                Map.of("file", "backstory.yaml"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.BACKSTORY, Files.BACKSTORY.replace("neighbor: true", "neighbor: 1"))
                        .source(),
                ErrorCode.CONTENT_MALFORMED,
                Map.of("file", "backstory.yaml"));
        assertContentError(
                Files.valid()
                        .with(
                                ContentLoader.BACKSTORY,
                                Files.BACKSTORY.replace("revanchism: 400", "revanchism: \"400\""))
                        .source(),
                ErrorCode.CONTENT_MALFORMED,
                Map.of("file", "backstory.yaml", "location", "fragments[0].weight_tags.revanchism"));
    }

    @Test
    void unknownBranchOrStatusKeyIsReported() {
        assertContentError(
                Files.valid()
                        .with(ContentLoader.DEVELOPMENT, Files.DEVELOPMENT.replace("id: society", "id: culture"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "file", "development.yaml",
                        "location", "branches[2]",
                        "cause", "unknown_reference",
                        "field", "tech_branch",
                        "value", "culture"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.NUCLEAR, Files.NUCLEAR.replace("id: arsenal", "id: ARSENAL"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("file", "nuclear.yaml", "location", "statuses[2]", "cause", "unknown_reference"));
    }

    @Test
    void missingBranchLevelOrStatusIsReportedAtItsList() {
        assertContentError(
                Files.valid()
                        .with(
                                ContentLoader.DEVELOPMENT,
                                Files.DEVELOPMENT.replace("  - { id: military, name: Військо }\n", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "branches", "cause", "missing_definition", "value", "military"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.DEVELOPMENT, Files.DEVELOPMENT.replace("level: 2,", "level: 1,"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "levels[5]", "cause", "duplicate_id", "value", 1));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.DEVELOPMENT, Files.DEVELOPMENT.replaceAll("(?m)^  - \\{ level: 2,.*\n", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "levels", "cause", "missing_definition", "value", 2));
        assertContentError(
                Files.valid()
                        .with(
                                ContentLoader.NUCLEAR,
                                "statuses:\n  - { id: none, name: Немає, weight: 80, quality: 50 }\n")
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("file", "nuclear.yaml", "location", "statuses", "value", "program"));
    }

    @Test
    void developmentLevelOutsideScaleIsReported() {
        assertContentError(
                Files.valid()
                        .with(ContentLoader.DEVELOPMENT, Files.DEVELOPMENT.replace("level: 2,", "level: 3,"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "levels[5]", "cause", "value_out_of_range", "value", 3L));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.DEVELOPMENT, Files.DEVELOPMENT.replace("level: 0,", "level: \"0\","))
                        .source(),
                ErrorCode.CONTENT_MALFORMED,
                Map.of("file", "development.yaml"));
    }

    @Test
    void developmentLevelNeedsWeightAndQuality() {
        assertContentError(
                Files.valid()
                        .with(ContentLoader.DEVELOPMENT, Files.DEVELOPMENT.replace(", weight: 30", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "levels[3]", "cause", "blank_value", "field", "weight"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.DEVELOPMENT, Files.DEVELOPMENT.replace("quality: 95", "quality: 101"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "levels[5]", "cause", "value_out_of_range", "field", "development_level.2.quality"));
        assertContentError(
                Files.valid()
                        .with(
                                ContentLoader.DEVELOPMENT,
                                Files.DEVELOPMENT.replace("weight: 8, quality: 95", "weight: 0, quality: 95"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "levels[5]", "cause", "value_out_of_range", "field", "development_level.2.weight"));
    }

    @Test
    void developmentLevelTagIsASourceForBackstoryConditions() {
        String backstory = Files.BACKSTORY.replace("requires: [lost_war]", "requires: [lost_war, backward]");

        assertThat(ContentLoader.load(Files.valid()
                                .with(ContentLoader.BACKSTORY, backstory)
                                .source())
                        .backstory()
                        .fragments())
                .hasSize(2);
        assertContentError(
                Files.valid()
                        .with(ContentLoader.BACKSTORY, backstory)
                        .with(ContentLoader.DEVELOPMENT, Files.DEVELOPMENT.replace(", tags: [backward]", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "fragments[1]", "cause", "unknown_reference", "value", "backward"));
    }

    @Test
    void loadsGdpLevelsInContentOrder() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        assertThat(pack.gdpLevels())
                .extracting(GdpLevelDef::id, GdpLevelDef::perCapita, GdpLevelDef::tier, GdpLevelDef::weight)
                .containsExactly(
                        tuple(new GdpLevelId("poor"), 250, OutcomeTier.CRIT_FAIL, 30),
                        tuple(new GdpLevelId("middle"), 1000, OutcomeTier.PARTIAL, 40),
                        tuple(new GdpLevelId("rich"), 3500, OutcomeTier.CRIT_SUCCESS, 30));
        GdpLevelDef rich = pack.gdpLevel(new GdpLevelId("rich")).orElseThrow();
        assertThat(rich.name()).isEqualTo("Заможність");
        assertThat(rich.quality()).isEqualTo(80);
        assertThat(rich.tags()).containsExactly("rich");
        assertThat(pack.gdpLevel(new GdpLevelId("middle")).orElseThrow().tags()).isEmpty();
    }

    @Test
    void invalidGdpLevelIsReportedAtItsPosition() {
        assertGdpError(
                Files.GDP.replace("per_capita: 1000, ", ""),
                Map.of("location", "levels[1]", "cause", "blank_value", "field", "per_capita"));
        assertGdpError(
                Files.GDP.replace("tier: partial", "tier: average"),
                Map.of("location", "levels[1]", "cause", "unknown_reference", "field", "tier", "value", "average"));
        assertGdpError(
                Files.GDP.replace("tier: crit_success", "tier: CRIT_SUCCESS"),
                Map.of("location", "levels[2]", "cause", "unknown_reference"));
        assertGdpError(
                Files.GDP.replace("per_capita: 250", "per_capita: 0"),
                Map.of("location", "levels[0]", "cause", "value_out_of_range", "field", "gdp_level.poor.per_capita"));
        assertGdpError(
                Files.GDP.replace("id: rich", "id: middle"), Map.of("location", "levels[2]", "cause", "duplicate_id"));
        assertGdpError("levels: []\n", Map.of("location", "levels", "cause", "empty_collection"));
    }

    @Test
    void gdpLevelsMustGoFromPoorToRich() {
        assertGdpError(
                Files.GDP.replace("per_capita: 3500", "per_capita: 1000"),
                Map.of(
                        "location",
                        "levels[2]",
                        "cause",
                        "out_of_order",
                        "field",
                        "gdp_level.rich.per_capita",
                        "value",
                        1000));
        assertGdpError(
                Files.GDP.replace("tier: crit_success", "tier: fail"),
                Map.of(
                        "location",
                        "levels[2]",
                        "cause",
                        "out_of_order",
                        "field",
                        "gdp_level.rich.tier",
                        "value",
                        "fail"));
    }

    @Test
    void gdpLevelTagIsASourceForBackstoryConditions() {
        String backstory = Files.BACKSTORY.replace("requires: [lost_war]", "requires: [lost_war, rich]");

        assertThat(ContentLoader.load(Files.valid()
                                .with(ContentLoader.BACKSTORY, backstory)
                                .source())
                        .backstory()
                        .fragments())
                .hasSize(2);
        assertContentError(
                Files.valid()
                        .with(ContentLoader.BACKSTORY, backstory)
                        .with(ContentLoader.GDP, Files.GDP.replace(", tags: [rich]", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "fragments[1]", "cause", "unknown_reference", "value", "rich"));
    }

    @Test
    void loadsHdiLevelsInContentOrder() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        assertThat(pack.hdiLevels())
                .extracting(HdiLevelDef::id, HdiLevelDef::hdi, HdiLevelDef::tier, HdiLevelDef::weight)
                .containsExactly(
                        tuple(new HdiLevelId("low"), 30, OutcomeTier.CRIT_FAIL, 30),
                        tuple(new HdiLevelId("middle"), 60, OutcomeTier.PARTIAL, 40),
                        tuple(new HdiLevelId("high"), 80, OutcomeTier.CRIT_SUCCESS, 30));
        HdiLevelDef high = pack.hdiLevel(new HdiLevelId("high")).orElseThrow();
        assertThat(high.name()).isEqualTo("Високий розвиток");
        assertThat(high.quality()).isEqualTo(80);
        assertThat(high.tags()).containsExactly("educated");
        assertThat(pack.hdiLevel(new HdiLevelId("middle")).orElseThrow().tags()).isEmpty();
    }

    @Test
    void invalidHdiLevelIsReportedAtItsPosition() {
        assertHdiError(
                Files.HDI.replace("hdi: 60, ", ""),
                Map.of("location", "levels[1]", "cause", "blank_value", "field", "hdi"));
        assertHdiError(
                Files.HDI.replace("tier: partial", "tier: average"),
                Map.of("location", "levels[1]", "cause", "unknown_reference", "field", "tier", "value", "average"));
        assertHdiError(
                Files.HDI.replace("hdi: 80", "hdi: 101"),
                Map.of("location", "levels[2]", "cause", "value_out_of_range", "field", "hdi_level.high.hdi"));
        assertHdiError(
                Files.HDI.replace("id: high", "id: middle"), Map.of("location", "levels[2]", "cause", "duplicate_id"));
        assertHdiError("levels: []\n", Map.of("location", "levels", "cause", "empty_collection"));
    }

    @Test
    void hdiLevelsMustGoFromLowToHigh() {
        assertHdiError(
                Files.HDI.replace("hdi: 80", "hdi: 60"),
                Map.of("location", "levels[2]", "cause", "out_of_order", "field", "hdi_level.high.hdi", "value", 60));
        assertHdiError(
                Files.HDI.replace("tier: crit_success", "tier: fail"),
                Map.of(
                        "location",
                        "levels[2]",
                        "cause",
                        "out_of_order",
                        "field",
                        "hdi_level.high.tier",
                        "value",
                        "fail"));
    }

    @Test
    void hdiLevelTagIsASourceForBackstoryConditions() {
        String backstory = Files.BACKSTORY.replace("requires: [lost_war]", "requires: [lost_war, educated]");

        assertThat(ContentLoader.load(Files.valid()
                                .with(ContentLoader.BACKSTORY, backstory)
                                .source())
                        .backstory()
                        .fragments())
                .hasSize(2);
        assertContentError(
                Files.valid()
                        .with(ContentLoader.BACKSTORY, backstory)
                        .with(ContentLoader.HDI, Files.HDI.replace(", tags: [educated]", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "fragments[1]", "cause", "unknown_reference", "value", "educated"));
    }

    @Test
    void loadsArmySizesInContentOrder() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        assertThat(pack.armySizes())
                .extracting(ArmySizeDef::id, ArmySizeDef::shareBp, ArmySizeDef::tier, ArmySizeDef::weight)
                .containsExactly(
                        tuple(new ArmySizeId("small"), 40, OutcomeTier.CRIT_FAIL, 30),
                        tuple(new ArmySizeId("regular"), 150, OutcomeTier.PARTIAL, 40),
                        tuple(new ArmySizeId("large"), 300, OutcomeTier.CRIT_SUCCESS, 30));
        ArmySizeDef large = pack.armySize(new ArmySizeId("large")).orElseThrow();
        assertThat(large.name()).isEqualTo("Велика армія");
        assertThat(large.quality()).isEqualTo(60);
        assertThat(large.tags()).containsExactly("large_army");
        assertThat(pack.armySize(new ArmySizeId("regular")).orElseThrow().tags())
                .isEmpty();
    }

    @Test
    void invalidArmySizeIsReportedAtItsPosition() {
        assertArmyError(
                Files.ARMY.replace("share_bp: 150, ", ""),
                Map.of("location", "sizes[1]", "cause", "blank_value", "field", "share_bp"));
        assertArmyError(
                Files.ARMY.replace("tier: partial", "tier: average"),
                Map.of("location", "sizes[1]", "cause", "unknown_reference", "field", "tier", "value", "average"));
        assertArmyError(
                Files.ARMY.replace("share_bp: 300", "share_bp: 10001"),
                Map.of("location", "sizes[2]", "cause", "value_out_of_range", "field", "army_size.large.share_bp"));
        assertArmyError(
                Files.ARMY.replace("id: large", "id: regular"),
                Map.of("location", "sizes[2]", "cause", "duplicate_id"));
        assertArmyError("sizes: []\n", Map.of("location", "sizes", "cause", "empty_collection"));
    }

    @Test
    void armySizesMustGoFromSmallToLarge() {
        assertArmyError(
                Files.ARMY.replace("share_bp: 300", "share_bp: 150"),
                Map.of(
                        "location",
                        "sizes[2]",
                        "cause",
                        "out_of_order",
                        "field",
                        "army_size.large.share_bp",
                        "value",
                        150));
        assertArmyError(
                Files.ARMY.replace("tier: crit_success", "tier: fail"),
                Map.of(
                        "location",
                        "sizes[2]",
                        "cause",
                        "out_of_order",
                        "field",
                        "army_size.large.tier",
                        "value",
                        "fail"));
    }

    @Test
    void armySizeTagIsASourceForBackstoryConditions() {
        String backstory = Files.BACKSTORY.replace("requires: [lost_war]", "requires: [lost_war, small_army]");

        assertThat(ContentLoader.load(Files.valid()
                                .with(ContentLoader.BACKSTORY, backstory)
                                .source())
                        .backstory()
                        .fragments())
                .hasSize(2);
        assertContentError(
                Files.valid()
                        .with(ContentLoader.BACKSTORY, backstory)
                        .with(ContentLoader.ARMY, Files.ARMY.replace(", tags: [small_army]", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "fragments[1]", "cause", "unknown_reference", "value", "small_army"));
    }

    @Test
    void loadsTrainingLevelsFromMilitiaToElite() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        assertThat(pack.trainingLevels().values())
                .extracting(
                        TrainingLevelDef::level,
                        TrainingLevelDef::combatModifier,
                        TrainingLevelDef::tier,
                        TrainingLevelDef::weight)
                .containsExactly(
                        tuple(1, -20, OutcomeTier.CRIT_FAIL, 10),
                        tuple(2, -10, OutcomeTier.FAIL, 25),
                        tuple(3, 0, OutcomeTier.PARTIAL, 35),
                        tuple(4, 10, OutcomeTier.SUCCESS, 20),
                        tuple(5, 20, OutcomeTier.CRIT_SUCCESS, 10));
        TrainingLevelDef elite = pack.trainingLevel(5);
        assertThat(elite.name()).isEqualTo("Еліта");
        assertThat(elite.quality()).isEqualTo(90);
        assertThat(elite.tags()).containsExactly("elite_army");
        assertThat(pack.trainingLevel(3).tags()).isEmpty();
    }

    @Test
    void invalidTrainingLevelIsReportedAtItsPosition() {
        assertArmyError(
                Files.ARMY.replace("combat_modifier: 0, ", ""),
                Map.of("location", "training[2]", "cause", "blank_value", "field", "combat_modifier"));
        assertArmyError(
                Files.ARMY.replace("level: 3, ", ""),
                Map.of("location", "training[2]", "cause", "blank_value", "field", "level"));
        assertArmyError(
                Files.ARMY.replace("tier: success", "tier: good"),
                Map.of("location", "training[3]", "cause", "unknown_reference", "field", "tier", "value", "good"));
        assertArmyError(
                Files.ARMY.replace("combat_modifier: 20", "combat_modifier: 101"),
                Map.of(
                        "location",
                        "training[4]",
                        "cause",
                        "value_out_of_range",
                        "field",
                        "training_level.5.combat_modifier"));
        assertArmyError(
                Files.ARMY.replace("level: 4", "level: 3"),
                Map.of("location", "training[3]", "cause", "duplicate_id", "value", 3));
        assertArmyError(
                Files.ARMY.replace("level: 5", "level: 6"),
                Map.of("location", "training[4]", "cause", "value_out_of_range", "field", "training_level.level"));
    }

    @Test
    void everyTrainingLevelMustBeDefined() {
        assertArmyError(
                Files.ARMY.substring(0, Files.ARMY.indexOf("  - { level: 5")),
                Map.of("location", "training", "cause", "missing_definition", "field", "training", "value", 5));
        assertArmyError(
                Files.ARMY.substring(0, Files.ARMY.indexOf("training:")),
                Map.of("location", "training", "cause", "missing_definition", "field", "training", "value", 1));
    }

    @Test
    void trainingLevelsMustGoFromMilitiaToElite() {
        assertArmyError(
                Files.ARMY.replace("combat_modifier: 10", "combat_modifier: 0"),
                Map.of(
                        "location",
                        "training[3]",
                        "cause",
                        "out_of_order",
                        "field",
                        "training_level.4.combat_modifier",
                        "value",
                        0));
        assertArmyError(
                Files.ARMY.replace("tier: crit_success, weight: 10", "tier: fail, weight: 10"),
                Map.of(
                        "location",
                        "training[4]",
                        "cause",
                        "out_of_order",
                        "field",
                        "training_level.5.tier",
                        "value",
                        "fail"));
        String swapped = Files.ARMY
                .replace("level: 4, name: Ветерани", "level: 9, name: Ветерани")
                .replace("level: 5, name: Еліта", "level: 4, name: Еліта")
                .replace("level: 9, name: Ветерани", "level: 5, name: Ветерани");
        assertArmyError(
                swapped,
                Map.of(
                        "location",
                        "training[4]",
                        "cause",
                        "out_of_order",
                        "field",
                        "training_level.4.level",
                        "value",
                        4));
    }

    @Test
    void trainingTagIsASourceForBackstoryConditions() {
        String backstory = Files.BACKSTORY.replace("requires: [lost_war]", "requires: [lost_war, elite_army]");

        assertThat(ContentLoader.load(Files.valid()
                                .with(ContentLoader.BACKSTORY, backstory)
                                .source())
                        .backstory()
                        .fragments())
                .hasSize(2);
        assertContentError(
                Files.valid()
                        .with(ContentLoader.BACKSTORY, backstory)
                        .with(ContentLoader.ARMY, Files.ARMY.replace(", tags: [elite_army]", ""))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "fragments[1]", "cause", "unknown_reference", "value", "elite_army"));
    }

    @Test
    void loadsStreakWheels() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        StreakWheelDef golden = pack.streaks().wheel(StreakKind.GOLDEN_AGE);
        assertThat(golden.name()).isEqualTo("Золота доба");
        assertThat(golden.tags()).containsExactly("golden_age", "world_attention");
        assertThat(golden.rewards())
                .extracting(
                        StreakRewardDef::id,
                        StreakRewardDef::weight,
                        StreakRewardDef::durationYears,
                        StreakRewardDef::fateTokens,
                        StreakRewardDef::extraPeople)
                .containsExactly(
                        tuple(new StreakRewardId("national_pride"), 100, 10, 0, 0),
                        tuple(new StreakRewardId("great_figure"), 100, 0, 0, 1));
        assertThat(golden.rewards().getFirst().modifiers())
                .containsExactly(new ModifierDef(ModifierTarget.stat(Stat.STABILITY), 10));
        StreakRewardDef chance = pack.streaks()
                .wheel(StreakKind.UNDERDOG)
                .reward(new StreakRewardId("second_chance"))
                .orElseThrow();
        assertThat(chance.fateTokens()).isEqualTo(2);
        assertThat(chance.modifiers()).isEmpty();
        assertThat(chance.tags()).isEmpty();
    }

    @Test
    void invalidStreakWheelIsReportedAtItsPosition() {
        assertStreaksError(
                Files.STREAKS.replace("id: underdog", "id: bad_luck"),
                Map.of("location", "wheels[1]", "cause", "unknown_reference", "field", "streak", "value", "bad_luck"));
        assertStreaksError(
                Files.STREAKS.replace("id: underdog", "id: golden_age"),
                Map.of("location", "wheels[1]", "cause", "duplicate_id", "value", "golden_age"));
        assertStreaksError(
                Files.STREAKS.substring(0, Files.STREAKS.indexOf("  - id: underdog")),
                Map.of("location", "wheels", "cause", "missing_definition", "value", "underdog"));
        assertStreaksError(
                Files.STREAKS.replace("    description: Доля била державу знову й знову.\n", ""),
                Map.of("location", "wheels[1]", "cause", "blank_value", "field", "streak.underdog.description"));
        assertStreaksError(
                Files.STREAKS.replace("id: great_figure", "id: national_pride"),
                Map.of("location", "wheels[0].rewards[1]", "cause", "duplicate_id", "value", "national_pride"));
    }

    @Test
    void invalidStreakRewardIsReportedAtItsPosition() {
        assertStreaksError(
                Files.STREAKS.replace("        weight: 100\n        fate_tokens: 2", "        fate_tokens: 2"),
                Map.of("location", "wheels[1].rewards[0]", "cause", "blank_value", "field", "weight"));
        assertStreaksError(
                Files.STREAKS.replace("fate_tokens: 2", "fate_tokens: 4"),
                Map.of(
                        "location",
                        "wheels[1].rewards[0]",
                        "cause",
                        "value_out_of_range",
                        "field",
                        "streak_reward.second_chance.fate_tokens"));
        assertStreaksError(
                Files.STREAKS.replace("extra_people: 1", "extra_people: 0"),
                Map.of(
                        "location",
                        "wheels[0].rewards[1]",
                        "cause",
                        "empty_collection",
                        "field",
                        "streak_reward.great_figure.effects"));
        assertStreaksError(
                Files.STREAKS.replace("duration: 10", "duration: 101"),
                Map.of("location", "wheels[0].rewards[0]", "cause", "value_out_of_range"));
        assertStreaksError(
                Files.STREAKS.replace("stat:stability", "stat:happiness"),
                Map.of("location", "wheels[0].rewards[0].modifiers[0]", "cause", "unknown_reference"));
        assertStreaksError(
                Files.STREAKS.substring(0, Files.STREAKS.indexOf("  - id: underdog")) + """
                          - id: underdog
                            name: Андердог
                            description: Доля била державу знову й знову.
                            rewards: []
                        """,
                Map.of("location", "wheels[1].rewards", "cause", "empty_collection"));
    }

    @Test
    void streakTagIsASourceForBackstoryConditions() {
        String backstory = Files.BACKSTORY.replace("requires: [lost_war]", "requires: [lost_war, world_attention]");

        assertThat(ContentLoader.load(Files.valid()
                                .with(ContentLoader.BACKSTORY, backstory)
                                .source())
                        .backstory()
                        .fragments())
                .hasSize(2);
        assertContentError(
                Files.valid()
                        .with(ContentLoader.BACKSTORY, backstory)
                        .with(ContentLoader.STREAKS, Files.STREAKS.replace(", world_attention]", "]"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "fragments[1]", "cause", "unknown_reference", "value", "world_attention"));
    }

    @Test
    void loadsReligions() {
        ContentPack pack = ContentLoader.load(Files.valid().source());
        ReligionContent religions = pack.religions();

        assertThat(religions.archetypes())
                .extracting(ArchetypeDef::id, ArchetypeDef::figure, ArchetypeDef::weight)
                .containsExactly(
                        tuple(new ArchetypeId("monotheism"), "Єдиний Бог", 20),
                        tuple(new ArchetypeId("polytheism"), "Верховне божество", 20));
        assertThat(religions.aspects().getFirst().weightFor(Set.of("archetype_polytheism")))
                .isEqualTo(130);
        DogmaDef holyWar = religions.dogma(new DogmaId("holy_war")).orElseThrow();
        assertThat(holyWar.modifiers()).containsExactly(new ModifierDef(ModifierTarget.stat(Stat.WAR_WEARINESS), -5));
        assertThat(religions.compatible(new DogmaId("pacifism"), new DogmaId("holy_war")))
                .isFalse();
        assertThat(religions.polities())
                .extracting(polity -> polity.id().value())
                .containsExactly("single_church", "communities");
        FaithFormDef path = religions.faithForm(new FaithFormId("path")).orElseThrow();
        assertThat(path.gender()).isEqualTo(GrammaticalGender.MASCULINE);
        assertThat(path.figureCase()).isEqualTo(GrammaticalCase.GENITIVE);
        assertThat(path.render(GrammaticalCase.DATIVE, "Оріна")).isEqualTo("Шляхові Оріна");
        assertThat(religions
                        .archetype(new ArchetypeId("polytheism"))
                        .orElseThrow()
                        .figureSexes())
                .containsExactly(Sex.MALE, Sex.FEMALE);
        assertThat(pack.balance().religion())
                .isEqualTo(new ReligionBalanceDef(
                        List.of(
                                new ReligionCountDef(8, new CountRange(3, 4)),
                                new ReligionCountDef(40, new CountRange(4, 6))),
                        new CountRange(2, 3),
                        new CountRange(2, 4)));
    }

    @Test
    void invalidReligionsAreReportedAtTheirPosition() {
        assertReligionsError(
                Files.RELIGIONS.replace("figure_sexes: [male, female]", "figure_sexes: [male, other]"),
                Map.of("location", "archetypes[1].figure_sexes[1]", "cause", "unknown_reference", "value", "other"));
        assertReligionsError(
                Files.RELIGIONS.replace("figure_sexes: [male, female]", "figure_sexes: [female, female]"),
                Map.of(
                        "location",
                        "archetypes[1]",
                        "cause",
                        "duplicate_id",
                        "field",
                        "archetype.polytheism.figure_sexes"));
        assertReligionsError(
                Files.RELIGIONS.replace("    figure_sexes: [male]\n", ""),
                Map.of(
                        "location",
                        "archetypes[0]",
                        "cause",
                        "empty_collection",
                        "field",
                        "archetype.monotheism.figure_sexes"));
        assertReligionsError(
                Files.RELIGIONS.replace("id: polytheism", "id: monotheism"),
                Map.of("location", "archetypes[1]", "cause", "duplicate_id", "value", "monotheism"));
        assertReligionsError(
                Files.RELIGIONS.replace("    figure: Єдиний Бог\n", ""),
                Map.of("location", "archetypes[0]", "cause", "blank_value", "field", "archetype.monotheism.figure"));
        assertReligionsError(
                Files.RELIGIONS.replace(
                        "    weight: 100\n    tags: [religion_knowledge]", "    tags: [religion_knowledge]"),
                Map.of("location", "aspects[1]", "cause", "blank_value", "field", "weight"));
        assertReligionsError(
                Files.RELIGIONS.replace("incompatible: [pacifism]", "incompatible: [asceticism]"),
                Map.of("location", "dogmas[0].incompatible[0]", "cause", "unknown_reference", "value", "asceticism"));
        assertReligionsError(
                Files.RELIGIONS.replace("incompatible: [pacifism]", "incompatible: [holy_war]"),
                Map.of("location", "dogmas[0]", "cause", "self_reference"));
        assertReligionsError(
                Files.RELIGIONS.replace("stat:war_weariness", "stat:faith"),
                Map.of("location", "dogmas[0].modifiers[0]", "cause", "unknown_reference"));
        assertReligionsError(
                Files.RELIGIONS.replace("archetypes: [monotheism, polytheism]", "archetypes: [monotheism, dualism]"),
                Map.of("location", "faith_forms[0].archetypes[1]", "cause", "unknown_reference", "value", "dualism"));
        assertReligionsError(
                Files.RELIGIONS.replace("archetypes: [monotheism, polytheism]", "archetypes: [monotheism]"),
                Map.of("location", "faith_forms", "cause", "missing_definition", "value", "polytheism"));
        assertReligionsError(
                Files.RELIGIONS.replace("figure_case: genitive", "figure_case: ablative"),
                Map.of("location", "faith_forms[0]", "cause", "unknown_reference", "value", "ablative"));
        assertReligionsError(
                Files.RELIGIONS.replace("\"Шляхом {figure}\"", "\"Шляхом\""),
                Map.of(
                        "location",
                        "faith_forms[0]",
                        "cause",
                        "invalid_name_format",
                        "field",
                        "faith_form.path.templates.instrumental"));
        assertReligionsError(
                Files.RELIGIONS.replace(
                        "  - id: pacifism", "  - id: pacifism\n    weight_tags: { polity_single_church: 10 }"),
                Map.of(
                        "location",
                        "dogmas[1].weight_tags",
                        "cause",
                        "unknown_reference",
                        "value",
                        "polity_single_church"));
        assertReligionsError(
                Files.RELIGIONS.replace(
                        "weight_tags: { archetype_polytheism: 30 }", "weight_tags: { dogma_holy_war: 30 }"),
                Map.of("location", "aspects[0].weight_tags", "cause", "unknown_reference", "value", "dogma_holy_war"));
        assertReligionsError(
                Files.RELIGIONS.substring(0, Files.RELIGIONS.indexOf("polities:")) + "polities: []\n"
                        + Files.RELIGIONS.substring(Files.RELIGIONS.indexOf("faith_forms:")),
                Map.of("location", "polities", "cause", "empty_collection"));
    }

    @Test
    void loadsStateReligion() {
        StateReligionDef stateReligion =
                ContentLoader.load(Files.valid().source()).religions().stateReligion();

        assertThat(stateReligion.religionWeight()).isEqualTo(100);
        SecularStateDef secular = stateReligion.secular();
        assertThat(secular.name()).isEqualTo("Світська держава");
        assertThat(secular.tags()).containsExactly("secular");
        assertThat(secular.weightFor(Set.of("democratic"))).isEqualTo(100);
        assertThat(secular.weightFor(Set.of("democratic", "revanchism"))).isZero();
    }

    @Test
    void religionTagsAreBackstoryTagSources() {
        String backstory = Files.BACKSTORY.replace(
                "excludes: [nuclear_power]",
                "excludes: [nuclear_power, secular]\n    weight_tags: { dogma_holy_war: 50 }");

        assertThat(ContentLoader.load(Files.valid()
                                .with(ContentLoader.BACKSTORY, backstory)
                                .source())
                        .backstory()
                        .fragments())
                .hasSize(2);
    }

    @Test
    void invalidStateReligionIsReportedAtItsPosition() {
        assertReligionsError(
                Files.RELIGIONS.substring(0, Files.RELIGIONS.indexOf("state_religion:")),
                Map.of("location", "state_religion", "cause", "blank_value", "field", "state_religion"));
        assertReligionsError(
                Files.RELIGIONS.replace("  religion_weight: 100\n", ""),
                Map.of("location", "state_religion", "cause", "blank_value", "field", "religion_weight"));
        assertReligionsError(
                Files.RELIGIONS.replace("    weight: 50\n", ""),
                Map.of("location", "state_religion.secular", "cause", "blank_value", "field", "weight"));
        // backward — мітка рівня розвиненості: колесо розвиненості крутиться вже після колеса релігії.
        assertReligionsError(
                Files.RELIGIONS.replace("weight_tags: { democratic: 50 }", "weight_tags: { backward: 50 }"),
                Map.of("location", "state_religion.secular", "cause", "unknown_reference", "value", "backward"));
        assertReligionsError(
                Files.RELIGIONS.replace("excludes: [revanchism]", "excludes: [theocratic]"),
                Map.of("location", "state_religion.secular", "cause", "unknown_reference", "value", "theocratic"));
        assertReligionsError(
                Files.RELIGIONS.replace("excludes: [revanchism]", "requires: [revanchism]\n    excludes: [revanchism]"),
                Map.of("location", "state_religion.secular", "cause", "duplicate_id", "value", "revanchism"));
    }

    @Test
    void missingReligionBalanceIsAnError() {
        String withoutReligion = Files.BALANCE.substring(0, Files.BALANCE.indexOf("religion:"));
        assertContentError(
                balance(withoutReligion),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "religion", "cause", "blank_value", "field", "religion"));
        assertContentError(
                balance(withoutReligion + "religion:\n  aspects: { min: 2, max: 3 }\n"),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "religion.dogmas", "cause", "blank_value"));
        assertContentError(
                balance(Files.BALANCE.replace("dogmas: { min: 2, max: 4 }", "dogmas: { min: 2, max: 7 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "religion", "cause", "value_out_of_range", "field", "religion.dogmas.max"));
    }

    @Test
    void invalidReligionCountIsReportedAtItsPosition() {
        assertContentError(
                balance(Files.BALANCE.replace(
                        "    - { max_countries: 8, min: 3, max: 4 }\n    - { max_countries: 40, min: 4, max: 6 }\n",
                        "    []\n")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "religion", "cause", "empty_collection", "field", "religion.count"));
        assertContentError(
                balance(Files.BALANCE.replace("max_countries: 40", "max_countries: 8")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "religion", "cause", "out_of_order", "field", "religion.count[1].max_countries"));
        assertContentError(
                balance(Files.BALANCE.replace("{ max_countries: 40, min: 4, max: 6 }", "{ min: 4, max: 6 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "religion.count[1]", "cause", "blank_value", "field", "max_countries"));
        assertContentError(
                balance(Files.BALANCE.replace(
                        "{ max_countries: 40, min: 4, max: 6 }", "{ max_countries: 40, min: 4 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "religion.count[1]", "cause", "blank_value", "field", "max"));
        assertContentError(
                balance(Files.BALANCE.replace(
                        "{ max_countries: 8, min: 3, max: 4 }", "{ max_countries: 8, min: 0, max: 4 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "religion.count[0]", "cause", "value_out_of_range", "field", "religion.count.min"));
    }

    @Test
    void loadsBalance() {
        BalanceDef balance = ContentLoader.load(Files.valid().source()).balance();

        assertThat(balance.wheel().strength(new WheelKind("economic_cycle"))).isEqualTo(80);
        assertThat(balance.wheel().strength(new WheelKind("construction"))).isEqualTo(50);
        assertThat(balance.wheel().investmentCurve()).containsExactly(20, 12, 7, 4);
        assertThat(balance.streaks()).isEqualTo(new StreakRulesDef(85, 15, 3));
        assertThat(balance.corridor(PowerCorridor.CLASSIC).players()).isEqualTo(new MedianRange(50, 200));
        assertThat(balance.corridor(PowerCorridor.FULL_CHAOS).npc()).isEqualTo(new MedianRange(10, 1000));
        assertThat(balance.generation().backstoryFragments()).isEqualTo(new CountRange(2, 4));
        assertThat(balance.generation().notablePeople()).isEqualTo(new CountRange(1, 3));
        assertThat(balance.generation().warheads()).isEqualTo(new CountRange(2, 10));
        assertThat(balance.generation().nuclearEnergyAdvantage()).isEqualTo(10);
        assertThat(balance.generation().gdpDevelopmentAdvantage()).isEqualTo(10);
        assertThat(balance.generation().hdiGdpAdvantage()).isEqualTo(15);
        assertThat(balance.generation().armySizeGdpAdvantage()).isEqualTo(10);
        assertThat(balance.generation().armyTrainingGdpAdvantage()).isEqualTo(10);
        assertThat(balance.generation().armyTrainingDevelopmentAdvantage()).isEqualTo(10);
        assertThat(balance.generation().personTraits()).isEqualTo(new CountRange(1, 3));
        assertThat(balance.generation().personAge()).isEqualTo(new CountRange(25, 70));
        assertThat(balance.generation().nameCandidates()).isEqualTo(5);
    }

    @Test
    void loadsMapTemplatesAndWorldBalance() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        assertThat(pack.map().templates())
                .extracting(
                        MapTemplateDef::id,
                        MapTemplateDef::name,
                        MapTemplateDef::weight,
                        MapTemplateDef::provincesPct,
                        MapTemplateDef::continents)
                .containsExactly(
                        tuple(new MapTemplateId("pangaea"), "Пангея", 20, 90, new CountRange(1, 1)),
                        tuple(new MapTemplateId("archipelago"), "Архіпелаг", 15, 120, new CountRange(5, 8)));
        WorldBalanceDef world = pack.balance().world();
        assertThat(world.npcExtra(NpcShare.FEW)).isEqualTo(new CountRange(1, 3));
        assertThat(world.npcExtra(NpcShare.NORMAL)).isEqualTo(new CountRange(2, 6));
        assertThat(world.npcExtra(NpcShare.MANY)).isEqualTo(new CountRange(4, 10));
        assertThat(world.provincesPerCountry()).isEqualTo(new StepRange(60, 100, 5));
        assertThat(world.unclaimedBp()).isEqualTo(new StepRange(500, 1500, 100));
        assertThat(world.provinces()).isEqualTo(new CountRange(400, 3500));
    }

    @Test
    void invalidMapIsReportedAtItsPosition() {
        assertMapError(
                Files.MAP.replace("id: archipelago", "id: pangaea"),
                Map.of("location", "templates[1]", "cause", "duplicate_id", "value", "pangaea"));
        assertMapError(
                Files.MAP.replace("    weight: 15\n", ""),
                Map.of("location", "templates[1]", "cause", "blank_value", "field", "weight"));
        assertMapError(
                Files.MAP.replace("continents: { min: 5, max: 8 }", "continents: { min: 0, max: 8 }"),
                Map.of(
                        "location",
                        "templates[1]",
                        "cause",
                        "value_out_of_range",
                        "field",
                        "map_template.archipelago.continents.min"));
        assertMapError(
                Files.MAP.replace("continents: { min: 5, max: 8 }", "continents: { min: 5 }"),
                Map.of("location", "templates[1].continents", "cause", "blank_value", "field", "max"));
        assertMapError("templates: []\n", Map.of("location", "templates", "cause", "empty_collection"));
    }

    @Test
    void invalidWorldBalanceIsReportedAtItsPosition() {
        assertContentError(
                balance(Files.BALANCE.substring(0, Files.BALANCE.indexOf("world:"))),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "world", "cause", "blank_value", "field", "world"));
        assertContentError(
                balance(Files.BALANCE.replace("    few: {", "    some: {")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "world.npc_extra.some", "cause", "unknown_reference", "value", "some"));
        assertContentError(
                balance(Files.BALANCE.replace("    many: { min: 4, max: 10 }\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "world.npc_extra", "cause", "missing_definition", "value", "many"));
        assertContentError(
                balance(Files.BALANCE.replace("{ min: 60, max: 100, step: 5 }", "{ min: 60, max: 100, step: 7 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "world.provinces_per_country", "cause", "value_out_of_range", "step", 7));
        assertContentError(
                balance(Files.BALANCE.replace("{ min: 500, max: 1500, step: 100 }", "{ min: 500, max: 1500 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "world.unclaimed_bp", "cause", "blank_value", "field", "step"));
        assertContentError(
                balance(Files.BALANCE.replace(
                        "{ min: 500, max: 1500, step: 100 }", "{ min: 500, max: 6000, step: 100 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "world", "cause", "value_out_of_range", "field", "world.unclaimed_bp.max"));
    }

    @Test
    void strengthOverridesAreOptional() {
        BalanceDef balance = ContentLoader.load(Files.valid()
                        .with(ContentLoader.BALANCE, Files.BALANCE.replace("  strength:\n    economic_cycle: 80\n", ""))
                        .source())
                .balance();

        assertThat(balance.wheel().strengths()).isEmpty();
    }

    @Test
    void invalidBalanceIsReportedAtItsPosition() {
        assertContentError(
                balance(Files.BALANCE.replace("[20, 12, 7, 4]", "[20, 12, 12]")),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "file", "balance.yaml",
                        "location", "wheel",
                        "cause", "value_out_of_range",
                        "field", "wheel.investment_curve[2]"));
        assertContentError(
                balance(Files.BALANCE.replace("economic_cycle: 80", "EconomicCycle: 80")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "wheel.strength.EconomicCycle", "cause", "invalid_key_format"));
        assertContentError(
                balance(Files.BALANCE.replace("very_bad_quality: 15", "very_bad_quality: 90")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "streaks", "field", "streaks.very_good_quality"));
        assertContentError(
                balance(Files.BALANCE.replace(
                        "npc: { min_pct: 33, max_pct: 300 }", "npc: { min_pct: 60, max_pct: 300 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "power_corridors[1]", "field", "power_corridor.classic.npc.min_pct"));
        assertContentError(
                balance(Files.BALANCE.replace("min_pct: 20, max_pct: 500", "min_pct: 20, max_pct: 50")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "power_corridors[2].players", "field", "max_pct"));
        assertContentError(
                balance(Files.BALANCE.replace("id: full_chaos", "id: anarchy")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "power_corridors[2]", "cause", "unknown_reference", "value", "anarchy"));
        assertContentError(
                balance(Files.BALANCE.replace("id: full_chaos", "id: classic")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "power_corridors[2]", "cause", "duplicate_id", "value", "classic"));
        assertContentError(
                balance(Files.BALANCE.replace(
                        "notable_people: { min: 1, max: 3 }", "notable_people: { min: 0, max: 3 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.notable_people.min"));
        assertContentError(
                balance(Files.BALANCE.replace("warheads: { min: 2, max: 10 }", "warheads: { min: 0, max: 10 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.warheads.min"));
        assertContentError(
                balance(Files.BALANCE.replace("nuclear_energy_advantage: 10", "nuclear_energy_advantage: 101")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.nuclear_energy_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("gdp_development_advantage: 10", "gdp_development_advantage: -1")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.gdp_development_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("hdi_gdp_advantage: 15", "hdi_gdp_advantage: 101")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.hdi_gdp_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("army_size_gdp_advantage: 10", "army_size_gdp_advantage: -1")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.army_size_gdp_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("army_training_gdp_advantage: 10", "army_training_gdp_advantage: 101")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.army_training_gdp_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace(
                        "army_training_development_advantage: 10", "army_training_development_advantage: -1")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.army_training_development_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace(
                        "person_traits: { min: 1, max: 3 }", "person_traits: { min: 1, max: 4 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.person_traits.max"));
        assertContentError(
                balance(Files.BALANCE.replace("person_age: { min: 25, max: 70 }", "person_age: { min: 10, max: 70 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.person_age.min"));
        assertContentError(
                balance(Files.BALANCE.replace("name_candidates: 5", "name_candidates: 13")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "field", "generation.name_candidates"));
    }

    @Test
    void missingBalanceNumbersAreErrorsNotZeros() {
        assertContentError(
                balance(Files.BALANCE.replace("  length: 3\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "streaks", "cause", "blank_value", "field", "streaks.length"));
        assertContentError(
                balance(Files.BALANCE.replace("{ min: 2, max: 4 }", "{ min: 2 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation.backstory_fragments", "cause", "blank_value", "field", "max"));
        assertContentError(
                balance(Files.BALANCE.replace("  warheads: { min: 2, max: 10 }\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation.warheads", "cause", "blank_value"));
        assertContentError(
                balance(Files.BALANCE.replace("  nuclear_energy_advantage: 10\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location",
                        "generation.nuclear_energy_advantage",
                        "cause",
                        "blank_value",
                        "field",
                        "nuclear_energy_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("  gdp_development_advantage: 10\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location",
                        "generation.gdp_development_advantage",
                        "cause",
                        "blank_value",
                        "field",
                        "gdp_development_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("  hdi_gdp_advantage: 15\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location",
                        "generation.hdi_gdp_advantage",
                        "cause",
                        "blank_value",
                        "field",
                        "hdi_gdp_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("  army_size_gdp_advantage: 10\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location",
                        "generation.army_size_gdp_advantage",
                        "cause",
                        "blank_value",
                        "field",
                        "army_size_gdp_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("  army_training_gdp_advantage: 10\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location",
                        "generation.army_training_gdp_advantage",
                        "cause",
                        "blank_value",
                        "field",
                        "army_training_gdp_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("  army_training_development_advantage: 10\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location",
                        "generation.army_training_development_advantage",
                        "cause",
                        "blank_value",
                        "field",
                        "army_training_development_advantage"));
        assertContentError(
                balance(Files.BALANCE.replace("  person_traits: { min: 1, max: 3 }\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation.person_traits", "cause", "blank_value"));
        assertContentError(
                balance(Files.BALANCE.replace("person_age: { min: 25, max: 70 }", "person_age: { min: 25 }")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation.person_age", "cause", "blank_value", "field", "max"));
        assertContentError(
                balance(Files.BALANCE.replace("  name_candidates: 5\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation.name_candidates", "cause", "blank_value", "field", "name_candidates"));
        assertContentError(
                balance(Files.BALANCE.replace("    players: { min_pct: 75, max_pct: 133 }\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "power_corridors[0].players", "cause", "blank_value"));
        assertContentError(
                balance(Files.BALANCE.substring(0, Files.BALANCE.indexOf("generation:"))),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "generation", "cause", "blank_value"));
        assertContentError(
                balance(Files.BALANCE.replaceAll("(?s)  - id: full_chaos.*?max_pct: 1000 }\n", "")),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "power_corridors", "cause", "missing_definition", "value", "full_chaos"));
    }

    @Test
    void missingFileIsReported() {
        assertContentError(
                Files.valid().without(ContentLoader.DOCTRINES).source(),
                ErrorCode.CONTENT_FILE_MISSING,
                Map.of("file", "doctrines.yaml"));
    }

    @Test
    void readFailureIsReported() {
        ContentSource broken = name -> {
            throw new IOException("диск");
        };

        assertThatThrownBy(() -> ContentLoader.load(broken)).isInstanceOfSatisfying(ContentException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.CONTENT_READ_FAILED);
            assertThat(e.details()).containsEntry("file", "ideologies.yaml");
            assertThat(e.getCause()).isInstanceOf(IOException.class);
        });
    }

    @Test
    void invalidValueIsReportedWithFileLocationAndCause() {
        String yaml = Files.IDEOLOGIES.replace("id: revanchism", "id: Revanchism");

        assertContentError(
                Files.valid().with(ContentLoader.IDEOLOGIES, yaml).source(),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "file", "ideologies.yaml",
                        "location", "ideologies[1].sub_ideologies[0]",
                        "cause", "invalid_key_format",
                        "field", "sub_ideology_id",
                        "value", "Revanchism"));
    }

    @Test
    void invalidModifierIsReportedAtItsPosition() {
        String yaml = Files.IDEOLOGIES.replace("value: 10", "value: 150");

        assertContentError(
                Files.valid().with(ContentLoader.IDEOLOGIES, yaml).source(),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location", "ideologies[0].sub_ideologies[0].modifiers[0]",
                        "cause", "value_out_of_range",
                        "value", 150L));
    }

    @Test
    void ideologyWeightsAreRequiredAndPositive() {
        assertContentError(
                Files.valid()
                        .with(
                                ContentLoader.IDEOLOGIES,
                                Files.IDEOLOGIES.replace("демократія\n        weight: 100\n", "демократія\n"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location", "ideologies[0].sub_ideologies[0]",
                        "cause", "blank_value",
                        "field", "weight"));
        assertContentError(
                Files.valid()
                        .with(ContentLoader.IDEOLOGIES, Files.IDEOLOGIES.replace("weight: 300", "weight: 0"))
                        .source(),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location", "ideologies[0]",
                        "cause", "value_out_of_range",
                        "field", "ideology.democracy.weight"));
    }

    @Test
    void unknownModifierTargetIsReported() {
        String yaml = Files.IDEOLOGIES.replace("stat:hdi", "stat:happiness");

        assertContentError(
                Files.valid().with(ContentLoader.IDEOLOGIES, yaml).source(),
                ErrorCode.INVALID_CONTENT,
                Map.of(
                        "location",
                        "ideologies[0].modifiers[0]",
                        "cause",
                        "unknown_reference",
                        "value",
                        "stat:happiness"));
    }

    @Test
    void duplicateIdsAreReportedAtSecondOccurrence() {
        String resources = Files.RESOURCES.replace("id: oil", "id: iron");
        assertContentError(
                Files.valid().with(ContentLoader.RESOURCES, resources).source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("file", "resources.yaml", "location", "resources[1]", "cause", "duplicate_id", "value", "iron"));

        // Підкласифікації унікальні в усьому файлі, а не лише в межах ідеології.
        String ideologies = Files.IDEOLOGIES.replace("id: revanchism", "id: liberal_democracy");
        assertContentError(
                Files.valid().with(ContentLoader.IDEOLOGIES, ideologies).source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "ideologies[1].sub_ideologies[0]", "cause", "duplicate_id"));
    }

    @Test
    void emptyListAndEmptyItemAreReported() {
        assertContentError(
                Files.valid().with(ContentLoader.DOCTRINES, "doctrines: []\n").source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("file", "doctrines.yaml", "location", "doctrines", "cause", "empty_collection"));
        assertContentError(
                Files.valid().with(ContentLoader.RESOURCES, "resources:\n  -\n").source(),
                ErrorCode.INVALID_CONTENT,
                Map.of("location", "resources[0]", "cause", "blank_value"));
    }

    @Test
    void unknownFieldIsMalformedWithPath() {
        String yaml = Files.DOCTRINES.replace("tags: [land]", "tags: [land]\n    atack: 5");

        assertContentError(
                Files.valid().with(ContentLoader.DOCTRINES, yaml).source(),
                ErrorCode.CONTENT_MALFORMED,
                Map.of("file", "doctrines.yaml", "location", "doctrines[0].atack"));
    }

    @Test
    void strictYamlRejectsDuplicateKeysAndCoercions() {
        for (String broken : List.of(
                Files.DOCTRINES.replace("name: Бронетанкова", "name: Бронетанкова\n    name: Танкова"),
                Files.DOCTRINES.replace("name: Бронетанкова", "name: 42"),
                Files.DOCTRINES + "  - id: [oops\n")) {
            assertContentError(
                    Files.valid().with(ContentLoader.DOCTRINES, broken).source(),
                    ErrorCode.CONTENT_MALFORMED,
                    Map.of("file", "doctrines.yaml"));
        }
        String stringValue = Files.IDEOLOGIES.replace("value: 5", "value: \"5\"");
        assertContentError(
                Files.valid().with(ContentLoader.IDEOLOGIES, stringValue).source(),
                ErrorCode.CONTENT_MALFORMED,
                Map.of("file", "ideologies.yaml"));
        String missingValue = Files.IDEOLOGIES.replace(", value: 5", "");
        assertContentError(
                Files.valid().with(ContentLoader.IDEOLOGIES, missingValue).source(),
                ErrorCode.CONTENT_MALFORMED,
                Map.of("file", "ideologies.yaml"));
    }

    @Test
    void emptyFileIsMalformed() {
        assertContentError(
                Files.valid().with(ContentLoader.RESOURCES, "# лише коментар\n").source(),
                ErrorCode.CONTENT_MALFORMED,
                Map.of("file", "resources.yaml"));
    }

    @Test
    void hashIsStableAndTracksContent() {
        String hash = ContentLoader.load(Files.valid().source()).hash();

        assertThat(ContentLoader.load(Files.valid().source()).hash()).isEqualTo(hash);
        assertThat(ContentLoader.load(Files.valid()
                                .with(ContentLoader.RESOURCES, Files.RESOURCES.replace("\n", "\r\n"))
                                .source())
                        .hash())
                .as("CRLF не змінює хеш")
                .isEqualTo(hash);
        assertThat(ContentLoader.load(Files.valid()
                                .with(ContentLoader.RESOURCES, Files.RESOURCES.replace("Нафта", "Нафта-сирець"))
                                .source())
                        .hash())
                .isNotEqualTo(hash);
    }

    private static void assertBackstoryError(String backstory, Map<String, ?> expected) {
        assertThat(backstory).isNotEqualTo(Files.BACKSTORY);
        assertContentError(
                Files.valid().with(ContentLoader.BACKSTORY, backstory).source(), ErrorCode.INVALID_CONTENT, expected);
    }

    private static void assertGdpError(String gdp, Map<String, ?> expected) {
        assertThat(gdp).isNotEqualTo(Files.GDP);
        assertContentError(
                Files.valid().with(ContentLoader.GDP, gdp).source(),
                ErrorCode.INVALID_CONTENT,
                withFile(ContentLoader.GDP, expected));
    }

    private static void assertHdiError(String hdi, Map<String, ?> expected) {
        assertThat(hdi).isNotEqualTo(Files.HDI);
        assertContentError(
                Files.valid().with(ContentLoader.HDI, hdi).source(),
                ErrorCode.INVALID_CONTENT,
                withFile(ContentLoader.HDI, expected));
    }

    private static void assertStreaksError(String streaks, Map<String, ?> expected) {
        assertThat(streaks).isNotEqualTo(Files.STREAKS);
        assertContentError(
                Files.valid().with(ContentLoader.STREAKS, streaks).source(),
                ErrorCode.INVALID_CONTENT,
                withFile(ContentLoader.STREAKS, expected));
    }

    private static void assertArmyError(String army, Map<String, ?> expected) {
        assertThat(army).isNotEqualTo(Files.ARMY);
        assertContentError(
                Files.valid().with(ContentLoader.ARMY, army).source(),
                ErrorCode.INVALID_CONTENT,
                withFile(ContentLoader.ARMY, expected));
    }

    private static void assertReligionsError(String religions, Map<String, ?> expected) {
        assertThat(religions).isNotEqualTo(Files.RELIGIONS);
        assertContentError(
                Files.valid().with(ContentLoader.RELIGIONS, religions).source(),
                ErrorCode.INVALID_CONTENT,
                withFile(ContentLoader.RELIGIONS, expected));
    }

    private static void assertMapError(String map, Map<String, ?> expected) {
        assertThat(map).isNotEqualTo(Files.MAP);
        assertContentError(
                Files.valid().with(ContentLoader.MAP, map).source(),
                ErrorCode.INVALID_CONTENT,
                withFile(ContentLoader.MAP, expected));
    }

    private static Map<String, ?> withFile(String file, Map<String, ?> expected) {
        TreeMap<String, Object> all = new TreeMap<>(expected);
        all.put("file", file);
        return all;
    }

    private static void assertNamesError(String names, Map<String, ?> expected) {
        assertThat(names).isNotEqualTo(Files.NAMES);
        assertContentError(
                Files.valid().with(ContentLoader.NAMES, names).source(), ErrorCode.INVALID_CONTENT, expected);
    }

    private static ContentSource balance(String yaml) {
        return Files.valid().with(ContentLoader.BALANCE, yaml).source();
    }

    private static void assertContentError(ContentSource source, ErrorCode code, Map<String, ?> expected) {
        assertThatThrownBy(() -> ContentLoader.load(source)).isInstanceOfSatisfying(ContentException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            expected.forEach((key, value) -> assertThat(e.details()).contains(entry(key, value)));
        });
    }
}
