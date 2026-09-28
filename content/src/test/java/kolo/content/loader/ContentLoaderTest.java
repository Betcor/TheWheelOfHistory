package kolo.content.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.error.ContentException;
import kolo.engine.error.ErrorCode;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.Stat;
import kolo.engine.state.TechBranch;
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
        assertThat(pack.nuclearStatus(NuclearStatus.ARSENAL).tags()).containsExactly("nuclear_power");
        assertThat(pack.nuclearStatus(NuclearStatus.NONE).tags()).isEmpty();
    }

    @Test
    void loadsPersonKindsAndTraits() {
        ContentPack pack = ContentLoader.load(Files.valid().source());

        assertThat(pack.personKind(PersonKind.GENERAL).tags()).containsExactly("military");
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
                        .with(ContentLoader.NUCLEAR, "statuses:\n  - { id: none, name: Немає }\n")
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

    private static void assertContentError(ContentSource source, ErrorCode code, Map<String, ?> expected) {
        assertThatThrownBy(() -> ContentLoader.load(source)).isInstanceOfSatisfying(ContentException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            expected.forEach((key, value) -> assertThat(e.details()).contains(entry(key, value)));
        });
    }
}
