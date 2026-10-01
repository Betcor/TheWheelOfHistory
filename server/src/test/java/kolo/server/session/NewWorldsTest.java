package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.error.ValidationException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import kolo.engine.view.CountryView;
import kolo.engine.view.MapView;
import kolo.server.TestServers;
import org.junit.jupiter.api.Test;

/** Новий світ на вбудованому контенті: інтеграція сервера, завантажувача контенту й рушія. */
class NewWorldsTest {

    @Test
    void sameSeedSameWorld() {
        WorldState first = NewWorlds.generate(TestServers.CONTENT, 42, 2, NpcShare.NORMAL);
        WorldState second = NewWorlds.generate(TestServers.CONTENT, 42, 2, NpcShare.NORMAL);

        assertThat(second).isEqualTo(first);
        assertThat(first.seed()).isEqualTo(42);
        assertThat(first.contentHash()).isEqualTo(TestServers.CONTENT.hash());
    }

    @Test
    void differentSeedDifferentMap() {
        assertThat(TestServers.map(1, 1, NpcShare.FEW)).isNotEqualTo(TestServers.map(2, 1, NpcShare.FEW));
    }

    @Test
    void playersComeFirst() {
        MapView map = TestServers.map(7, 3, NpcShare.FEW);

        assertThat(map.countries().stream().filter(CountryView::player).map(CountryView::number))
                .containsExactly(0, 1, 2);
        assertThat(map.countries()).hasSizeGreaterThan(3);
    }

    @Test
    void invalidPlayersAreRejected() {
        assertThatThrownBy(() -> NewWorlds.generate(TestServers.CONTENT, 1, 0, NpcShare.FEW))
                .isInstanceOf(ValidationException.class);
    }
}
