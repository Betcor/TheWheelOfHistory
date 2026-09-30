package kolo.engine.generation.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.ResourceId;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.country.StartDeposit;
import kolo.engine.generation.country.StartPerson;
import kolo.engine.generation.map.MapCell;
import kolo.engine.generation.map.PlacementMap;
import kolo.engine.generation.map.SeaZone;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.state.CellKind;
import kolo.engine.state.ControlType;
import kolo.engine.state.Country;
import kolo.engine.state.CountryId;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.GameMap;
import kolo.engine.state.MapTile;
import kolo.engine.state.Person;
import kolo.engine.state.PersonId;
import kolo.engine.state.Province;
import kolo.engine.state.ProvinceId;
import kolo.engine.state.Religion;
import kolo.engine.state.ReligionId;
import kolo.engine.state.SeaZoneId;
import kolo.engine.state.SeaZoneState;
import kolo.engine.state.WorldInvariants;
import kolo.engine.state.WorldState;
import kolo.engine.wheel.RollRecord;

/**
 * Стан світу на 1970 рік зі згенерованого світу ({@link StartWorld}): без кидків, лише перенесення.
 *
 * <p>Номери зі спільного лічильника id — за порядком генерації: спершу держави (номер id = номер на карті, як у
 * {@link CountryId#of(long)} під час генерації), далі релігії, далі люди — держава за державою. Столиця —
 * найлюдніша провінція держави, при рівності — з найменшим номером.
 */
public final class WorldStates {

    private WorldStates() {}

    /**
     * @param seed seed, з якого згенеровано світ
     * @param content контент, з яким згенеровано світ (його хеш фіксується в стані)
     * @throws kolo.engine.error.InvariantViolationException якщо зібраний стан порушує інваріанти
     */
    public static WorldState of(long seed, ContentPack content, StartWorld world) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(world, "world");
        WorldMap map = world.map();
        long nextId = 0;

        List<CountryId> countryIds = new ArrayList<>();
        for (int n = 0; n < world.countries().size(); n++) {
            countryIds.add(CountryId.of(nextId++));
        }
        List<ReligionId> religionIds = new ArrayList<>();
        for (int r = 0; r < world.religions().religions().size(); r++) {
            religionIds.add(ReligionId.of(nextId++));
        }

        TreeMap<ProvinceId, Province> provinces = provinces(map, world, countryIds);
        TreeMap<CountryId, Country> countries = new TreeMap<>();
        TreeMap<PersonId, Person> people = new TreeMap<>();
        for (int n = 0; n < world.countries().size(); n++) {
            StartCountry start = world.country(n);
            CountryId id = countryIds.get(n);
            List<PersonId> personIds = new ArrayList<>();
            for (StartPerson person : start.people().people()) {
                PersonId personId = PersonId.of(nextId++);
                personIds.add(personId);
                people.put(
                        personId,
                        new Person(
                                personId,
                                id,
                                person.name(),
                                person.kind(),
                                person.sex(),
                                person.traits(),
                                person.bornTurn(),
                                true));
            }
            ControlType control = n < map.size().players() ? ControlType.PLAYER : ControlType.NPC;
            countries.put(id, country(id, control, start, capital(start), religionIds, personIds));
        }

        TreeMap<ReligionId, Religion> religions = new TreeMap<>();
        for (int r = 0; r < religionIds.size(); r++) {
            StartReligion religion = world.religions().religions().get(r);
            religions.put(
                    religionIds.get(r),
                    new Religion(
                            religionIds.get(r),
                            religion.archetype(),
                            religion.aspects(),
                            religion.dogmas(),
                            religion.polity(),
                            religion.faithForm(),
                            religion.figureSex(),
                            religion.figure(),
                            religion.name(),
                            religion.tags(),
                            ProvinceId.of(world.holyCenters().cell(r))));
        }

        List<RollRecord> rolls = new ArrayList<>(map.rolls());
        rolls.addAll(world.religions().rolls());
        rolls.addAll(world.holyCenters().rolls());

        WorldState state = new WorldState(
                WorldState.SCHEMA_VERSION,
                content.hash(),
                seed,
                0,
                nextId,
                gameMap(map),
                countries,
                provinces,
                people,
                religions,
                rolls);
        WorldInvariants.check(state);
        return state;
    }

    /** Карта стану: геометрія й географія кожної комірки, морські зони. */
    static GameMap gameMap(WorldMap map) {
        List<MapTile> tiles = new ArrayList<>(map.grid().cells().size());
        for (int n = 0; n < map.grid().cells().size(); n++) {
            tiles.add(tile(map, n));
        }
        List<SeaZoneState> zones = new ArrayList<>(map.sea().zones().size());
        for (int z = 0; z < map.sea().zones().size(); z++) {
            SeaZone zone = map.sea().zones().get(z);
            zones.add(new SeaZoneState(
                    SeaZoneId.of(z),
                    zone.cells(),
                    zone.neighbors().stream().map(SeaZoneId::of).toList()));
        }
        return new GameMap(map.grid().width(), map.grid().height(), tiles, zones);
    }

    private static MapTile tile(WorldMap map, int n) {
        MapCell cell = map.grid().cells().get(n);
        CellKind kind = map.sea().isSea(n) ? CellKind.SEA : map.sea().isLake(n) ? CellKind.LAKE : CellKind.LAND;
        boolean land = kind == CellKind.LAND;
        boolean river = map.rivers().hasRiver(n);
        OptionalInt zone = map.sea().zone(n);
        return new MapTile(
                cell.site(),
                cell.polygon(),
                cell.neighbors(),
                kind,
                land ? OptionalInt.of(map.continents().cellContinents().get(n)) : OptionalInt.empty(),
                kind == CellKind.SEA ? Optional.of(SeaZoneId.of(zone.getAsInt())) : Optional.empty(),
                land ? map.sea().seaZones(n).stream().map(SeaZoneId::of).toList() : List.of(),
                map.climate().terrain(n),
                map.relief().relief(n),
                map.climate().climate(n),
                map.relief().height(n),
                map.fertility().fertility(n),
                river,
                river ? map.rivers().downstream(n) : OptionalInt.empty());
    }

    private static TreeMap<ProvinceId, Province> provinces(WorldMap map, StartWorld world, List<CountryId> countryIds) {
        // Родовища й населення — за коміркою: провінція отримує їх від своєї держави.
        TreeMap<Integer, TreeSet<ResourceId>> deposits = new TreeMap<>();
        TreeMap<Integer, Integer> population = new TreeMap<>();
        for (StartCountry country : world.countries()) {
            for (StartDeposit deposit : country.resources().deposits()) {
                deposits.computeIfAbsent(deposit.cell(), cell -> new TreeSet<>())
                        .add(deposit.resource());
            }
            population.putAll(country.population().provinces());
        }
        TreeMap<ProvinceId, Province> provinces = new TreeMap<>();
        for (int n = 0; n < map.grid().cells().size(); n++) {
            if (map.sea().isWater(n)) {
                continue;
            }
            int number = map.placement().country(n);
            CountryId owner = number == PlacementMap.NONE ? null : countryIds.get(number);
            ProvinceId id = ProvinceId.of(n);
            provinces.put(
                    id,
                    new Province(
                            id,
                            owner,
                            owner,
                            population.getOrDefault(n, 0),
                            deposits.getOrDefault(n, new TreeSet<>()),
                            new TreeSet<>()));
        }
        return provinces;
    }

    /** Найлюдніша провінція; при рівності — з найменшим номером комірки. */
    static ProvinceId capital(StartCountry country) {
        int best = -1;
        int bestPopulation = -1;
        for (Map.Entry<Integer, Integer> entry :
                country.population().provinces().entrySet()) {
            // Обхід за зростанням номера: строго більше залишає меншу комірку при рівності.
            if (entry.getValue() > bestPopulation) {
                best = entry.getKey();
                bestPopulation = entry.getValue();
            }
        }
        return ProvinceId.of(best);
    }

    private static Country country(
            CountryId id,
            ControlType control,
            StartCountry start,
            ProvinceId capital,
            List<ReligionId> religionIds,
            List<PersonId> people) {
        OptionalInt religion = start.religion().religion();
        CountryOrigin origin = new CountryOrigin(
                start.territory().area(),
                start.population().level(),
                start.gdp().level(),
                start.hdi().level(),
                start.armySize().size(),
                start.backstory().entries().stream()
                        .map(entry ->
                                new CountryOrigin.Backstory(entry.fragment().id(), entry.year()))
                        .toList(),
                start.backstory().neighbor(),
                start.streaks().stream()
                        .map(bonus -> new CountryOrigin.Streak(
                                bonus.streak(), bonus.reward().id()))
                        .toList(),
                start.power().corridor(),
                start.power().strengthPct());
        return new Country(
                id,
                control,
                start.name().name(),
                start.name().style(),
                capital,
                start.regime().ideology().id(),
                start.regime().subIdeology().id(),
                religion.isPresent() ? religionIds.get(religion.getAsInt()) : null,
                start.development().levels(),
                start.gdp().perCapita(),
                start.hdi().hdi(),
                start.armySize().shareBp(),
                start.armyTraining().level(),
                start.nuclear().status(),
                start.nuclear().warheads(),
                start.fateTokens(),
                start.modifiers(),
                start.tags(),
                people,
                origin,
                start.rolls());
    }
}
