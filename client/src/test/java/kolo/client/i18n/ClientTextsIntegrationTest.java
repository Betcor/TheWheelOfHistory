package kolo.client.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.client.map.MapMode;
import kolo.engine.state.Climate;
import kolo.engine.state.NpcShare;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;
import org.junit.jupiter.api.Test;

/** Значення рушія, які показує клієнт, ↔ тексти: гравець не має побачити сирий ключ. */
class ClientTextsIntegrationTest {

    private static final Texts TEXTS = Texts.ukrainian();

    @Test
    void everyShownEngineValueHasText() {
        for (Terrain terrain : Terrain.values()) {
            assertThat(TEXTS.has("terrain." + terrain.key())).as(terrain.key()).isTrue();
        }
        for (Relief relief : Relief.values()) {
            assertThat(TEXTS.has("relief." + relief.key())).as(relief.key()).isTrue();
        }
        for (Climate climate : Climate.values()) {
            assertThat(TEXTS.has("climate." + climate.key())).as(climate.key()).isTrue();
        }
        for (NpcShare share : NpcShare.values()) {
            assertThat(TEXTS.has("npc_share." + share.key())).as(share.key()).isTrue();
        }
        for (MapMode mode : MapMode.values()) {
            assertThat(TEXTS.has("map.mode." + mode.key())).as(mode.key()).isTrue();
        }
    }
}
