package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import org.junit.jupiter.api.Test;

/** Смок: вбудований сервер стартує з вбудованим контентом і генерує світ за замовчуванням. */
class EmbeddedServerSmokeTest {

    @Test
    void generatesDefaultWorld() {
        MapView map = EmbeddedServer.withBundledContent().newWorld(1970, 1, NpcShare.NORMAL);

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
