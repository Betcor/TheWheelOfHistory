package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.protocol.message.ClientMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Смок: вбудований сервер стартує, вітає клієнта й віддає світ за замовчуванням через {@code LocalChannel}. */
class EmbeddedServerSmokeTest {

    @Test
    @Timeout(60)
    void generatesDefaultWorld() throws Exception {
        try (EmbeddedServer server = EmbeddedServer.startWithBundledContent();
                TestClient client = TestClient.welcomed(server.address(), server.contentHash())) {
            client.send(new ClientMessage.CreateWorld(1970, 1, NpcShare.NORMAL));
            MapView map = client.map();

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
