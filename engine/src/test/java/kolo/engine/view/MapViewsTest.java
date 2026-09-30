package kolo.engine.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import kolo.engine.generation.map.MapCell;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.world.StartWorld;
import kolo.engine.generation.world.WorldGenerator;
import kolo.engine.generation.world.WorldStates;
import kolo.engine.rng.Rng;
import kolo.engine.state.CellKind;
import kolo.engine.state.CountryId;
import kolo.engine.state.NpcShare;
import kolo.engine.state.Province;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;

class MapViewsTest {

    private static final long SEED = 1970;
    private static final StartWorld WORLD =
            WorldGenerator.generate(Rng.of(SEED), TestNames.PACK, WorldSizeInput.of(1, NpcShare.FEW));
    private static final WorldState STATE = WorldStates.of(SEED, TestNames.PACK, WORLD);
    private static final MapView VIEW = MapViews.of(STATE);

    @Test
    void geometryIsGrid() {
        WorldMap map = WORLD.map();
        assertThat(VIEW.seed()).isEqualTo(SEED);
        assertThat(VIEW.width()).isEqualTo(map.grid().width());
        assertThat(VIEW.height()).isEqualTo(map.grid().height());
        assertThat(VIEW.cellSide()).isEqualTo(map.grid().cellSide());
        assertThat(VIEW.cells()).hasSameSizeAs(map.grid().cells());
        for (int n = 0; n < VIEW.cells().size(); n++) {
            MapCell cell = map.grid().cells().get(n);
            CellView view = VIEW.cells().get(n);
            assertThat(view.site()).isEqualTo(cell.site());
            assertThat(view.polygon()).isEqualTo(cell.polygon());
            assertThat(view.neighbors()).isEqualTo(cell.neighbors());
        }
    }

    @Test
    void cellsFollowMapLayers() {
        WorldMap map = WORLD.map();
        for (int n = 0; n < VIEW.cells().size(); n++) {
            CellView view = VIEW.cells().get(n);
            CellKind kind = map.sea().isSea(n) ? CellKind.SEA : map.sea().isLake(n) ? CellKind.LAKE : CellKind.LAND;
            assertThat(view.kind()).as("cell %d", n).isEqualTo(kind);
            assertThat(view.terrain()).isEqualTo(map.climate().terrain(n));
            assertThat(view.relief()).isEqualTo(map.relief().relief(n));
            assertThat(view.climate()).isEqualTo(map.climate().climate(n));
            assertThat(view.height()).isEqualTo(map.relief().height(n));
            assertThat(view.fertility()).isEqualTo(map.fertility().fertility(n));
            assertThat(view.river()).isEqualTo(map.rivers().hasRiver(n));
            assertThat(view.downstream()).isEqualTo(view.river() ? map.rivers().downstream(n) : OptionalInt.empty());
            int owner = map.placement().country(n);
            assertThat(view.country()).isEqualTo(owner < 0 ? OptionalInt.empty() : OptionalInt.of(owner));
        }
    }

    @Test
    void countriesKeepNamesAndPlayers() {
        assertThat(VIEW.countries()).hasSize(WORLD.countries().size());
        for (CountryView country : VIEW.countries()) {
            int n = country.number();
            assertThat(country.name()).isEqualTo(WORLD.country(n).name().name());
            assertThat(country.player()).isEqualTo(n < WORLD.map().size().players());
            assertThat(country.provinces()).isEqualTo(WORLD.map().country(n).provinces());
            long owned = VIEW.cells().stream()
                    .filter(cell -> cell.country().equals(OptionalInt.of(n)))
                    .count();
            assertThat(owned).isEqualTo(country.provinces());
        }
        assertThat(VIEW.countries().stream().filter(CountryView::player)).hasSize(1);
    }

    @Test
    void sameWorldSameView() {
        StartWorld again = WorldGenerator.generate(Rng.of(SEED), TestNames.PACK, WorldSizeInput.of(1, NpcShare.FEW));
        assertThat(MapViews.of(WorldStates.of(SEED, TestNames.PACK, again))).isEqualTo(VIEW);
    }

    @Test
    void viewFollowsStateNotGeneration() {
        WorldState changed = STATE.deepCopy();
        Province province = changed.provinces().values().stream()
                .filter(candidate -> candidate.owner().isPresent())
                .findFirst()
                .orElseThrow();
        CountryId other = changed.countries().keySet().stream()
                .filter(id -> !province.owner().orElseThrow().equals(id))
                .findFirst()
                .orElseThrow();
        province.setOwner(other);

        MapView view = MapViews.of(changed);

        int cell = Math.toIntExact(province.id().number());
        assertThat(view.cells().get(cell).country()).isEqualTo(OptionalInt.of(Math.toIntExact(other.number())));
        assertThat(VIEW.cells().get(cell).country())
                .isNotEqualTo(view.cells().get(cell).country());
    }
}
