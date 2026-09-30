package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.error.ValidationException;
import kolo.engine.state.NpcShare;
import kolo.engine.view.CountryView;
import kolo.engine.view.MapView;
import org.junit.jupiter.api.Test;

/** Вбудований сервер на вбудованому контенті: інтеграція сервера, завантажувача контенту й рушія. */
class EmbeddedServerTest {

    private static final EmbeddedServer SERVER = EmbeddedServer.withBundledContent();

    @Test
    void sameSeedSameMap() {
        MapView first = SERVER.newWorld(42, 2, NpcShare.NORMAL);
        MapView second = SERVER.newWorld(42, 2, NpcShare.NORMAL);

        assertThat(second).isEqualTo(first);
        assertThat(first.seed()).isEqualTo(42);
    }

    @Test
    void differentSeedDifferentMap() {
        assertThat(SERVER.newWorld(1, 1, NpcShare.FEW)).isNotEqualTo(SERVER.newWorld(2, 1, NpcShare.FEW));
    }

    @Test
    void playersComeFirst() {
        MapView map = SERVER.newWorld(7, 3, NpcShare.FEW);

        assertThat(map.countries().stream().filter(CountryView::player).map(CountryView::number))
                .containsExactly(0, 1, 2);
        assertThat(map.countries()).hasSizeGreaterThan(3);
    }

    @Test
    void invalidPlayersAreRejected() {
        assertThatThrownBy(() -> SERVER.newWorld(1, 0, NpcShare.FEW)).isInstanceOf(ValidationException.class);
    }
}
