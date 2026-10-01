package kolo.client.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;
import kolo.protocol.message.PlayerToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenStoreTest {

    private static final String WORLD = "0123456789abcdef0123456789abcdef";
    private static final String OTHER = "fedcba9876543210fedcba9876543210";

    @TempDir
    Path home;

    @Test
    void savedTokenIsFoundAfterRestart() {
        TokenStore store = new TokenStore(home.resolve("a").resolve(TokenStore.FILE_NAME));

        store.save(WORLD, List.of(new PlayerToken(2, "t".repeat(64))));

        assertThat(new TokenStore(store.file()).find(WORLD)).containsExactly(new PlayerToken(2, "t".repeat(64)));
        assertThat(store.find(OTHER)).isEmpty();
    }

    @Test
    void newTokenReplacesTheOldOneOfTheSameWorldOnly() {
        TokenStore store = new TokenStore(home.resolve(TokenStore.FILE_NAME));
        store.save(WORLD, List.of(new PlayerToken(1, "a")));
        store.save(OTHER, List.of(new PlayerToken(3, "b")));

        store.save(WORLD, List.of(new PlayerToken(1, "c")));

        assertThat(store.find(WORLD)).containsExactly(new PlayerToken(1, "c"));
        assertThat(store.find(OTHER)).containsExactly(new PlayerToken(3, "b"));
    }

    @Test
    void hotSeatWorldKeepsEverySeatInOrder() {
        TokenStore store = new TokenStore(home.resolve(TokenStore.FILE_NAME));
        List<PlayerToken> seats = List.of(new PlayerToken(2, "a"), new PlayerToken(1, "b"), new PlayerToken(3, "c"));

        store.save(WORLD, seats);

        assertThat(new TokenStore(store.file()).find(WORLD)).isEqualTo(seats);
        store.save(WORLD, List.of(new PlayerToken(2, "a")));
        assertThat(store.find(WORLD)).containsExactly(new PlayerToken(2, "a"));
        store.save(WORLD, List.of());
        assertThat(store.find(WORLD)).isEmpty();
    }

    @Test
    void missingOrDamagedEntriesAreAbsent() throws Exception {
        Path file = home.resolve(TokenStore.FILE_NAME);
        TokenStore store = new TokenStore(file);
        assertThat(store.find(WORLD)).isEmpty();

        Files.writeString(file, WORLD + "=без-двокрапки\n" + OTHER + "=0:t\nx=1:\ny=1:a,2\n");

        assertThat(store.find(WORLD)).isEmpty();
        assertThat(store.find(OTHER)).isEmpty();
        assertThat(store.find("x")).isEmpty();
        // Пошкоджене одне місце — пошкоджений увесь запис світу.
        assertThat(store.find("y")).isEmpty();
    }

    @Test
    void fileIsReadableOnlyByItsOwner() throws Exception {
        TokenStore store = new TokenStore(home.resolve(TokenStore.FILE_NAME));

        store.save(WORLD, List.of(new PlayerToken(1, "a")));

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(store.file())))
                    .isEqualTo("rw-------");
        }
        try (Stream<Path> files = Files.list(home)) {
            // Тимчасових файлів запису не лишилося.
            assertThat(files).containsExactly(store.file());
        }
    }

    @Test
    void unwritableLocationOnlyForgetsTheToken() throws Exception {
        Path directory = Files.createDirectory(home.resolve(TokenStore.FILE_NAME));
        TokenStore store = new TokenStore(directory);

        store.save(WORLD, List.of(new PlayerToken(1, "a")));

        assertThat(store.find(WORLD)).isEmpty();
    }
}
