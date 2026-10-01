package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import kolo.engine.error.ConflictException;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.NotFoundException;
import kolo.engine.error.SaveFileException;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldDirectoryTest {

    @TempDir
    Path root;

    @Test
    void defaultLocationPrefersTheHomeProperty() {
        assertThat(WorldDirectory.defaultLocation("/opt/kolo", "Windows 11", "C:/AppData", "/home/a"))
                .isEqualTo(Path.of("/opt/kolo", "worlds"));
    }

    @Test
    void defaultLocationOnWindowsIsInAppData() {
        assertThat(WorldDirectory.defaultLocation(null, "Windows 11", "C:/AppData", "C:/Users/a"))
                .isEqualTo(Path.of("C:/AppData", "Kolo", "worlds"));
        assertThat(WorldDirectory.defaultLocation(" ", "Windows 10", "", "C:/Users/a"))
                .isEqualTo(Path.of("C:/Users/a", ".kolo", "worlds"));
    }

    @Test
    void defaultLocationElsewhereIsInTheHomeDirectory() {
        assertThat(WorldDirectory.defaultLocation(null, "Linux", null, "/home/a"))
                .isEqualTo(Path.of("/home/a", ".kolo", "worlds"));
    }

    @Test
    void defaultLocationUsesTheTestHome() {
        // Тести отримують kolo.home від збірки: вбудований сервер не пише в домашню теку розробника.
        assertThat(System.getProperty(WorldDirectory.HOME_PROPERTY)).isNotBlank();
        assertThat(WorldDirectory.defaultLocation())
                .isEqualTo(Path.of(System.getProperty(WorldDirectory.HOME_PROPERTY), "worlds"));
    }

    @Test
    void createsTheDirectoryAndNamesTheFileBySeed() {
        WorldDirectory worlds = new WorldDirectory(root.resolve("a").resolve("worlds"));

        try (WorldStore store = create(worlds)) {
            assertThat(store.file()).isEqualTo(worlds.path().resolve("world-1" + WorldStore.EXTENSION));
            assertThat(store.meta().name()).isEqualTo("world-1");
        }
    }

    @Test
    void existingFilesAreNeverOverwritten() throws Exception {
        WorldDirectory worlds = new WorldDirectory(root);
        Files.writeString(root.resolve("world-1" + WorldStore.EXTENSION), "чужий файл");

        try (WorldStore second = create(worlds);
                WorldStore third = create(worlds)) {
            assertThat(second.file().getFileName()).hasToString("world-1-2" + WorldStore.EXTENSION);
            assertThat(third.file().getFileName()).hasToString("world-1-3" + WorldStore.EXTENSION);
            assertThat(third.meta().name()).isEqualTo("world-1-3");
        }
        assertThat(Files.readString(root.resolve("world-1" + WorldStore.EXTENSION)))
                .isEqualTo("чужий файл");
    }

    @Test
    void unusableDirectoryIsASaveError() throws Exception {
        Path file = Files.writeString(root.resolve("taken"), "x");

        assertThatThrownBy(() -> create(new WorldDirectory(file)))
                .isInstanceOfSatisfying(SaveFileException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.SAVE_FILE_ERROR);
                    assertThat(e.details()).containsEntry("operation", "create_directory");
                });
    }

    @Test
    void homeHoldsTheWorldsDirectory() {
        assertThat(WorldDirectory.home(null, "Linux", null, "/home/a")).isEqualTo(Path.of("/home/a", ".kolo"));
        assertThat(WorldDirectory.defaultLocation())
                .isEqualTo(WorldDirectory.home().resolve("worlds"));
    }

    @Test
    void nameIsTheFileNameWithoutTheExtension() {
        assertThat(WorldDirectory.name(Path.of("a", "world-5" + WorldStore.EXTENSION)))
                .isEqualTo("world-5");
        assertThat(WorldDirectory.name(Path.of("readme.txt"))).isEqualTo("readme.txt");
    }

    @Test
    void createdFileIsInUseUntilReleased() {
        WorldDirectory worlds = new WorldDirectory(root);
        Path file;
        try (WorldStore store = create(worlds)) {
            file = store.file();
            assertThat(worlds.inUse(file)).isTrue();
            assertThat(worlds.list()).isEmpty();
        }
        worlds.release(file);

        assertThat(worlds.inUse(file)).isFalse();
        assertThat(worlds.list()).extracting(WorldSummary::name).containsExactly("world-1");
    }

    @Test
    void listSkipsBrokenAndTemporaryFilesAndSortsByName() throws Exception {
        WorldDirectory worlds = new WorldDirectory(root);
        release(worlds, create(worlds));
        release(worlds, create(worlds));
        Files.writeString(root.resolve("broken" + WorldStore.EXTENSION), "не світ");
        Files.writeString(root.resolve(".world-9" + WorldStore.EXTENSION + ".creating"), "x");
        Files.writeString(root.resolve(".hidden" + WorldStore.EXTENSION), "x");
        Files.writeString(root.resolve("notes.txt"), "x");

        assertThat(worlds.list()).extracting(WorldSummary::name).containsExactly("world-1", "world-1-2");
    }

    @Test
    void missingDirectoryHasNoWorlds() {
        assertThat(new WorldDirectory(root.resolve("none")).list()).isEmpty();
    }

    @Test
    void openClaimsTheFileOnce() {
        WorldDirectory worlds = new WorldDirectory(root);
        release(worlds, create(worlds));

        Path file = worlds.open("world-1");

        assertThat(file)
                .isEqualTo(root.resolve("world-1" + WorldStore.EXTENSION).toAbsolutePath());
        assertThatThrownBy(() -> worlds.open("world-1")).isInstanceOfSatisfying(ConflictException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.WORLD_IN_USE);
            assertThat(e.details()).containsEntry("world", "world-1");
        });
        worlds.release(file);
        assertThat(worlds.open("world-1")).isEqualTo(file);
    }

    @Test
    void openFindsOnlyWorldsOfThisDirectory() throws Exception {
        WorldDirectory worlds = new WorldDirectory(root.resolve("worlds"));
        release(worlds, create(worlds));
        Files.writeString(root.resolve("outside" + WorldStore.EXTENSION), "x");

        for (String name : new String[] {"none", "../outside", "world-1" + WorldStore.EXTENSION, ""}) {
            assertThatThrownBy(() -> worlds.open(name))
                    .isInstanceOfSatisfying(
                            NotFoundException.class,
                            e -> assertThat(e.details())
                                    .containsEntry("what", "world")
                                    .containsEntry("id", name));
        }
    }

    private static void release(WorldDirectory worlds, WorldStore store) {
        store.close();
        worlds.release(store.file());
    }

    private static WorldStore create(WorldDirectory worlds) {
        WorldState state = TestWorlds.small();
        MapSnapshot map = MapSnapshot.of(state.map());
        return worlds.create(state.seed(), map, StateSnapshot.of(state, map));
    }
}
