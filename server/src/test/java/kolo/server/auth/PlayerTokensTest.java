package kolo.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerTokensTest {

    private final PlayerTokens tokens = new PlayerTokens();

    @Test
    void tokensAreLongHexAndDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = tokens.generate();
            assertThat(token).hasSize(PlayerTokens.TOKEN_BYTES * 2).matches("[0-9a-f]+");
            assertThat(seen.add(token)).isTrue();
        }
    }

    @Test
    void hashIsSha256OfTheToken() {
        // SHA-256("abc") — еталонний вектор FIPS 180-2.
        assertThat(PlayerTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void tokenMatchesOnlyItsHash() {
        String token = tokens.generate();
        String hash = PlayerTokens.hash(token);

        assertThat(PlayerTokens.matches(token, hash)).isTrue();
        assertThat(PlayerTokens.matches(tokens.generate(), hash)).isFalse();
        assertThat(PlayerTokens.matches(token, token)).isFalse();
    }
}
