package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Смок: вбудований сервер стартує, вітає клієнта, віддає світ за замовчуванням через {@code LocalChannel} і проводить
 * рік.
 */
class EmbeddedServerSmokeTest {

    @TempDir
    Path worlds;

    @Test
    @Timeout(60)
    void generatesDefaultWorld() throws Exception {
        try (EmbeddedServer server = EmbeddedServer.startWithBundledContent(worlds);
                TestClient client = TestClient.welcomed(server.address(), server.contentHash())) {
            MapView map = client.solo(1970, NpcShare.NORMAL);
            client.endYear(0);

            assertThat(map.cells()).isNotEmpty();
            assertThat(map.countries()).isNotEmpty();
            assertThat(map.cells().stream()
                            .filter(cell -> cell.country().isPresent())
                            .count())
                    .isEqualTo(map.countries().stream()
                            .mapToLong(country -> country.provinces())
                            .sum());
        }
    }
}
