package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;
import org.junit.jupiter.api.Test;

class GzipTest {

    @Test
    void roundTrips() {
        byte[] json = "{\"a\":[1,2,3],\"b\":\"колесо\"}".repeat(100).getBytes(StandardCharsets.UTF_8);

        byte[] compressed = Gzip.compress(json);

        assertThat(compressed.length).isLessThan(json.length);
        assertThat(Gzip.decompress(compressed, "state", "snapshots[0]")).isEqualTo(json);
    }

    @Test
    void damagedDataIsMalformed() {
        byte[] compressed = Gzip.compress(new byte[1000]);
        byte[] truncated = Arrays.copyOf(compressed, compressed.length / 2);

        assertThatThrownBy(() -> Gzip.decompress(truncated, "state", "snapshots[3]"))
                .isInstanceOfSatisfying(SaveFileException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.SAVE_MALFORMED);
                    assertThat(e.details())
                            .containsEntry("part", "state")
                            .containsEntry("location", "snapshots[3]")
                            .containsEntry("problem", "bad_gzip");
                });
        assertThatThrownBy(() -> Gzip.decompress(new byte[] {1, 2, 3}, "map", "world_map"))
                .isInstanceOf(SaveFileException.class);
    }
}
