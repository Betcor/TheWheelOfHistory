package kolo.engine.generation.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.country.StartDeposit;
import kolo.engine.generation.country.StartPerson;
import kolo.engine.generation.map.MapCell;
import kolo.engine.generation.map.PlacementMap;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.rng.Rng;
import kolo.engine.state.CellKind;
import kolo.engine.state.ControlType;
import kolo.engine.state.Country;
import kolo.engine.state.CountryId;
import kolo.engine.state.MapTile;
import kolo.engine.state.NpcShare;
import kolo.engine.state.Person;
import kolo.engine.state.PersonId;
import kolo.engine.state.Province;
import kolo.engine.state.ProvinceId;
import kolo.engine.state.Religion;
import kolo.engine.state.ReligionId;
import kolo.engine.state.SeaZoneId;
import kolo.engine.state.WorldInvariants;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;

class WorldStatesTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final long SEED = 1970;
    private static final StartWorld WORLD =
            WorldGenerator.generate(Rng.of(SEED), PACK, WorldSizeInput.of(2, NpcShare.FEW));
    private static final WorldState STATE = WorldStates.of(SEED, PACK, WORLD);

    @Test
    void stateStartsIn1970WithContentHashAndSeed() {
        assertThat(STATE.schemaVersion()).isEqualTo(WorldState.SCHEMA_VERSION);
        assertThat(STATE.contentHash()).isEqualTo(PACK.hash());
        assertThat(STATE.seed()).isEqualTo(SEED);
        assertThat(STATE.turn()).isZero();
        WorldInvariants.check(STATE);
    }

    @Test
    void sharedCounterGoesCountriesThenReligionsThenPeople() {
        int countries = WORLD.countries().size();
        int religions = WORLD.religions().religions().size();
        int people = WORLD.countries().stream()
                .mapToInt(country -> country.people().people().size())
                .sum();

        assertThat(STATE.countries().keySet().stream().map(CountryId::number))
                .containsExactlyInAnyOrderElementsOf(range(0, countries));
        assertThat(STATE.religions().keySet().stream().map(ReligionId::number))
                .containsExactlyInAnyOrderElementsOf(range(countries, countries + religions));
        assertThat(STATE.people().keySet().stream().map(PersonId::number))
                .containsExactlyInAnyOrderElementsOf(range(countries + religions, countries + religions + people));
        assertThat(STATE.nextIdSeq()).isEqualTo(countries + religions + people);
    }

    @Test
    void mapKeepsEveryCellAndZone() {
        WorldMap map = WORLD.map();
        assertThat(STATE.map().width()).isEqualTo(map.grid().width());
        assertThat(STATE.map().height()).isEqualTo(map.grid().height());
        assertThat(STATE.map().tiles()).hasSameSizeAs(map.grid().cells());
        assertThat(STATE.map().seaZones()).hasSameSizeAs(map.sea().zones());
        for (int n = 0; n < map.grid().cells().size(); n++) {
            MapCell cell = map.grid().cells().get(n);
            MapTile tile = STATE.map().tile(n);
            assertThat(tile.polygon()).isEqualTo(cell.polygon());
            assertThat(tile.neighbors()).isEqualTo(cell.neighbors());
            CellKind kind = map.sea().isSea(n) ? CellKind.SEA : map.sea().isLake(n) ? CellKind.LAKE : CellKind.LAND;
            assertThat(tile.kind()).as("cell %d", n).isEqualTo(kind);
            assertThat(tile.terrain()).isEqualTo(map.climate().terrain(n));
            assertThat(tile.fertility()).isEqualTo(map.fertility().fertility(n));
            assertThat(tile.river()).isEqualTo(map.rivers().hasRiver(n));
            assertThat(tile.coast().stream().map(zone -> (int) zone.number()).toList())
                    .isEqualTo(tile.isLand() ? map.sea().seaZones(n) : List.of());
            if (kind == CellKind.SEA) {
                assertThat(tile.seaZone())
                        .contains(SeaZoneId.of(map.sea().zone(n).getAsInt()));
            }
            if (tile.isLand()) {
                assertThat(tile.continent())
                        .hasValue(map.continents().cellContinents().get(n));
            }
        }
    }

    @Test
    void provincesFollowPlacementPopulationAndDeposits() {
        WorldMap map = WORLD.map();
        TreeSet<Integer> depositCells = new TreeSet<>();
        for (int n = 0; n < WORLD.countries().size(); n++) {
            StartCountry country = WORLD.country(n);
            for (Map.Entry<Integer, Integer> entry :
                    country.population().provinces().entrySet()) {
                Province province = STATE.provinces().get(ProvinceId.of(entry.getKey()));
                assertThat(province.owner()).contains(CountryId.of(n));
                assertThat(province.controller()).contains(CountryId.of(n));
                assertThat(province.populationK()).isEqualTo(entry.getValue());
            }
            for (StartDeposit deposit : country.resources().deposits()) {
                assertThat(STATE.provinces().get(ProvinceId.of(deposit.cell())).deposits())
                        .contains(deposit.resource());
                depositCells.add(deposit.cell());
            }
        }
        for (Province province : STATE.provinces().values()) {
            int cell = (int) province.id().number();
            if (map.placement().country(cell) == PlacementMap.NONE) {
                assertThat(province.owner()).isEmpty();
                assertThat(province.populationK()).isZero();
            }
            if (!depositCells.contains(cell)) {
                assertThat(province.deposits()).isEmpty();
            }
        }
        long total = STATE.provinces().values().stream()
                .mapToLong(province -> province.deposits().size())
                .sum();
        assertThat(total)
                .isEqualTo(WORLD.countries().stream()
                        .mapToLong(country -> country.resources().deposits().size())
                        .sum());
    }

    @Test
    void countriesCarryTheirGeneration() {
        for (int n = 0; n < WORLD.countries().size(); n++) {
            StartCountry start = WORLD.country(n);
            Country country = STATE.countries().get(CountryId.of(n));
            assertThat(country.control())
                    .isEqualTo(n < WORLD.map().size().players() ? ControlType.PLAYER : ControlType.NPC);
            assertThat(country.name()).isEqualTo(start.name().name());
            assertThat(country.nameStyle()).isEqualTo(start.name().style());
            assertThat(country.ideology()).isEqualTo(start.regime().ideology().id());
            assertThat(country.subIdeology())
                    .isEqualTo(start.regime().subIdeology().id());
            assertThat(country.startDevelopment()).isEqualTo(start.development().levels());
            assertThat(country.gdpPerCapita()).isEqualTo(start.gdp().perCapita());
            assertThat(country.hdi()).isEqualTo(start.hdi().hdi());
            assertThat(country.armyShareBp()).isEqualTo(start.armySize().shareBp());
            assertThat(country.training()).isEqualTo(start.armyTraining().level());
            assertThat(country.nuclear()).isEqualTo(start.nuclear().status());
            assertThat(country.warheads()).isEqualTo(start.nuclear().warheads());
            assertThat(country.fateTokens()).isEqualTo(start.fateTokens());
            assertThat(country.modifiers()).isEqualTo(start.modifiers());
            assertThat(country.tags()).isEqualTo(start.tags());
            assertThat(country.generationRolls()).isEqualTo(start.rolls());
            assertThat(country.origin().area()).isEqualTo(start.territory().area());
            assertThat(country.origin().backstoryNeighbor())
                    .isEqualTo(start.backstory().neighbor());
            assertThat(country.origin().backstory())
                    .hasSameSizeAs(start.backstory().entries());
            assertThat(country.origin().strengthPct()).isEqualTo(start.power().strengthPct());
            assertThat(country.religion().map(ReligionId::number).map(Math::toIntExact))
                    .isEqualTo(
                            start.religion().religion().isPresent()
                                    ? Optional.of(WORLD.countries().size()
                                            + start.religion().religion().getAsInt())
                                    : Optional.empty());
        }
    }

    @Test
    void capitalIsTheMostPopulousProvinceWithTheSmallestCellOnTies() {
        for (int n = 0; n < WORLD.countries().size(); n++) {
            Map<Integer, Integer> population = WORLD.country(n).population().provinces();
            int max = population.values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElseThrow();
            int first = population.entrySet().stream()
                    .filter(entry -> entry.getValue() == max)
                    .mapToInt(Map.Entry::getKey)
                    .min()
                    .orElseThrow();
            assertThat(STATE.countries().get(CountryId.of(n)).capital()).isEqualTo(ProvinceId.of(first));
        }
    }

    @Test
    void peopleBelongToTheirCountriesInOrder() {
        for (int n = 0; n < WORLD.countries().size(); n++) {
            List<StartPerson> start = WORLD.country(n).people().people();
            List<PersonId> ids = STATE.countries().get(CountryId.of(n)).people();
            assertThat(ids).hasSameSizeAs(start);
            for (int p = 0; p < ids.size(); p++) {
                Person person = STATE.people().get(ids.get(p));
                assertThat(person.country()).isEqualTo(CountryId.of(n));
                assertThat(person.name()).isEqualTo(start.get(p).name());
                assertThat(person.kind()).isEqualTo(start.get(p).kind());
                assertThat(person.sex()).isEqualTo(start.get(p).sex());
                assertThat(person.traits()).isEqualTo(start.get(p).traits());
                assertThat(person.bornTurn()).isEqualTo(start.get(p).bornTurn());
                assertThat(person.alive()).isTrue();
            }
        }
    }

    @Test
    void religionsKeepTheirPartsAndHolyCenters() {
        List<StartReligion> start = WORLD.religions().religions();
        int countries = WORLD.countries().size();
        for (int r = 0; r < start.size(); r++) {
            Religion religion = STATE.religions().get(ReligionId.of(countries + r));
            assertThat(religion.name()).isEqualTo(start.get(r).name());
            assertThat(religion.archetype()).isEqualTo(start.get(r).archetype());
            assertThat(religion.dogmas()).isEqualTo(start.get(r).dogmas());
            assertThat(religion.tags()).isEqualTo(start.get(r).tags());
            assertThat(religion.holyCenter())
                    .isEqualTo(ProvinceId.of(WORLD.holyCenters().cell(r)));
        }
    }

    @Test
    void worldRollsAreMapReligionsAndHolyCenters() {
        List<Object> expected = new ArrayList<>(WORLD.map().rolls());
        expected.addAll(WORLD.religions().rolls());
        expected.addAll(WORLD.holyCenters().rolls());

        assertThat(STATE.generationRolls()).isEqualTo(expected);
    }

    @Test
    void sameWorldGivesEqualStateAndCopyIsEqual() {
        StartWorld again = WorldGenerator.generate(Rng.of(SEED), PACK, WorldSizeInput.of(2, NpcShare.FEW));

        assertThat(WorldStates.of(SEED, PACK, again)).isEqualTo(STATE);
        assertThat(STATE.deepCopy()).isEqualTo(STATE);
    }

    private static List<Long> range(long from, long to) {
        List<Long> values = new ArrayList<>();
        for (long n = from; n < to; n++) {
            values.add(n);
        }
        return values;
    }
}
