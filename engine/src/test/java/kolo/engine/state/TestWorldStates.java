package kolo.engine.state;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.ArchetypeId;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.AspectId;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.DogmaId;
import kolo.engine.content.FaithFormId;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.HdiLevelId;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.PopulationLevelId;
import kolo.engine.content.ReligionPolityId;
import kolo.engine.content.ResourceId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TraitId;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;

/**
 * Маленький світ, зібраний вручну: 4 комірки в ряд — суходіл {@code prv_0} (держава {@code cty_0}, столиця),
 * {@code prv_1} (держава {@code cty_1}, столиця), нічийна {@code prv_2}, море {@code sea_0} і озеро в кутку; релігія
 * {@code rel_2} зі святим центром на нічийній землі, людина {@code per_3} у {@code cty_0}. Лічильник id — 4.
 */
public final class TestWorldStates {

    public static final CountryId FIRST = CountryId.of(0);
    public static final CountryId SECOND = CountryId.of(1);
    public static final ReligionId FAITH = ReligionId.of(2);
    public static final PersonId LEADER = PersonId.of(3);
    public static final ProvinceId FIRST_CAPITAL = ProvinceId.of(0);
    public static final ProvinceId SECOND_CAPITAL = ProvinceId.of(1);
    public static final ProvinceId UNCLAIMED = ProvinceId.of(2);

    private TestWorldStates() {}

    /** Новий незалежний стан щоразу. */
    public static WorldState state() {
        TreeMap<CountryId, Country> countries = new TreeMap<>();
        countries.put(FIRST, country(FIRST, ControlType.PLAYER, FIRST_CAPITAL, FAITH, List.of(LEADER)));
        countries.put(SECOND, country(SECOND, ControlType.NPC, SECOND_CAPITAL, null, List.of()));
        TreeMap<ProvinceId, Province> provinces = new TreeMap<>();
        provinces.put(FIRST_CAPITAL, province(FIRST_CAPITAL, FIRST, 500));
        provinces.put(SECOND_CAPITAL, province(SECOND_CAPITAL, SECOND, 300));
        provinces.put(UNCLAIMED, province(UNCLAIMED, null, 0));
        TreeMap<PersonId, Person> people = new TreeMap<>();
        people.put(LEADER, person(LEADER, FIRST));
        TreeMap<ReligionId, Religion> religions = new TreeMap<>();
        religions.put(FAITH, religion(FAITH, UNCLAIMED));
        return new WorldState(
                WorldState.SCHEMA_VERSION,
                "0".repeat(64),
                1970,
                0,
                4,
                map(),
                countries,
                provinces,
                people,
                religions,
                List.of());
    }

    /** Три клітинки суходолу, море й озеро. */
    public static GameMap map() {
        return new GameMap(
                500,
                100,
                List.of(
                        land(0, List.of(1), List.of()),
                        land(1, List.of(0, 2), List.of()),
                        land(2, List.of(1, 3), List.of(SeaZoneId.of(0))),
                        water(3, CellKind.SEA, List.of(2)),
                        water(4, CellKind.LAKE, List.of())),
                List.of(new SeaZoneState(SeaZoneId.of(0), List.of(3), List.of())));
    }

    public static MapTile land(int n, List<Integer> neighbors, List<SeaZoneId> coast) {
        return new MapTile(
                new GridPoint(n * 100 + 50, 50),
                square(n),
                neighbors,
                CellKind.LAND,
                OptionalInt.of(0),
                Optional.empty(),
                coast,
                Optional.of(Terrain.PLAIN),
                Optional.of(Relief.PLAIN),
                Optional.of(Climate.TEMPERATE),
                OptionalInt.of(20),
                OptionalInt.of(50),
                false,
                OptionalInt.empty());
    }

    public static MapTile water(int n, CellKind kind, List<Integer> neighbors) {
        return new MapTile(
                new GridPoint(n * 100 + 50, 50),
                square(n),
                neighbors,
                kind,
                OptionalInt.empty(),
                kind == CellKind.SEA ? Optional.of(SeaZoneId.of(0)) : Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false,
                OptionalInt.empty());
    }

    public static Country country(
            CountryId id, ControlType control, ProvinceId capital, ReligionId religion, List<PersonId> people) {
        return new Country(
                id,
                control,
                name("Велор"),
                new NameStyleId("northern"),
                capital,
                new IdeologyId("democracy"),
                new SubIdeologyId("liberal_democracy"),
                religion,
                development(Development.WORLD),
                1000,
                60,
                150,
                Training.REGULAR,
                NuclearStatus.NONE,
                0,
                1,
                List.of(new Modifier(
                        "ideology:democracy:0",
                        new ModifierSource(SourceKind.IDEOLOGY, "democracy"),
                        ModifierTarget.stat(Stat.STABILITY),
                        5,
                        null,
                        "ideology.democracy")),
                new TreeSet<>(List.of("democratic")),
                people,
                origin(),
                List.of());
    }

    public static CountryOrigin origin() {
        return new CountryOrigin(
                new AreaLevelId("medium"),
                new PopulationLevelId("medium"),
                new GdpLevelId("middle"),
                new HdiLevelId("middle"),
                new ArmySizeId("regular"),
                List.of(new CountryOrigin.Backstory(new BackstoryFragmentId("civil_war"), 1950)),
                Optional.empty(),
                List.of(),
                PowerCorridor.CLASSIC,
                100);
    }

    public static Province province(ProvinceId id, CountryId owner, int populationK) {
        return new Province(
                id, owner, owner, populationK, new TreeSet<>(List.of(new ResourceId("iron"))), new TreeSet<>());
    }

    public static Person person(PersonId id, CountryId country) {
        return new Person(
                id,
                country,
                name("Торвер"),
                PersonKind.values()[0],
                Sex.MALE,
                List.of(new TraitId("loyal")),
                -40,
                true);
    }

    public static Religion religion(ReligionId id, ProvinceId holyCenter) {
        return new Religion(
                id,
                new ArchetypeId("monotheism"),
                List.of(new AspectId("war")),
                List.of(new DogmaId("holy_war")),
                new ReligionPolityId("single_church"),
                new FaithFormId("path"),
                Sex.MALE,
                noun("Орін"),
                noun("Шлях Оріна"),
                new TreeSet<>(List.of("religion_war")),
                holyCenter);
    }

    public static LocalizedName name(String root) {
        return new LocalizedName(noun("Республіка " + root), noun(root));
    }

    private static NounPhrase noun(String text) {
        return new NounPhrase(GrammaticalGender.MASCULINE, List.of(text, text, text, text, text, text, text));
    }

    private static List<GridPoint> square(int n) {
        int x = n * 100;
        return List.of(
                new GridPoint(x, 0), new GridPoint(x + 100, 0), new GridPoint(x + 100, 100), new GridPoint(x, 100));
    }

    /** Розвиненість, де кожна галузь має цей рівень. */
    public static Map<TechBranch, Integer> development(int level) {
        EnumMap<TechBranch, Integer> development = new EnumMap<>(TechBranch.class);
        for (TechBranch branch : TechBranch.values()) {
            development.put(branch, level);
        }
        return development;
    }
}
