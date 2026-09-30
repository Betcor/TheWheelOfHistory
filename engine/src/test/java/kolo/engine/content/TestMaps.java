package kolo.engine.content;

import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import kolo.engine.state.Climate;
import kolo.engine.state.Cover;
import kolo.engine.state.NpcShare;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;

/**
 * Мінімальний валідний контент карти для тестових пакетів: шаблони «Пангея» (1 материк, коефіцієнт 100, суходолу 50%)
 * і «Архіпелаг» (3–5 материків, коефіцієнт 150, суходолу 30%); баланс розміру світу — {@link #BALANCE}.
 */
public final class TestMaps {

    /** Вологі пояси, де бувають ліс і болото. */
    private static final List<Climate> WET = List.of(Climate.BOREAL, Climate.TEMPERATE, Climate.TROPICAL);

    public static final MapTemplateDef PANGAEA = template("pangaea", 100, 100, 50, 1, 1);
    public static final MapTemplateDef ARCHIPELAGO = template("archipelago", 100, 150, 30, 3, 5);

    /** Комірка 20 одиниць, карта 2:1, одна ітерація Ллойда — дрібні карти для швидких тестів. */
    public static final MapGridDef GRID = new MapGridDef(20, 2, 1, 1);

    /** Вага материка 1–3, щонайменше 10 провінцій, береги наполовину з шуму, плями по 4 комірки. */
    public static final ContinentsDef CONTINENTS = new ContinentsDef(new CountRange(1, 3), 10, 50, 4);

    /**
     * 1–2 хребти, щонайменше 20 провінцій на хребет, довжина — поперечник материка, звертає до 20°; хребет +60, спад
     * 20 за крок, основа 20, шум ±20 плямами по 3 комірки; пагорби з 40, гори з 70.
     */
    public static final ReliefDef RELIEF = relief(new CountRange(1, 2), 20, 40, 70);

    /**
     * Світи: холодний −10 (вага 1), помірний 0 (вага 2), теплий +10 (вага 1). Температура: екватор 90, полюс 0,
     * охолодження 30% висоти, шум ±10; волога: берег 80, −10 за крок, шум ±20; плями по 3 комірки. Пороги: полярний
     * нижче 15, бореальний нижче 35, посушливий — волога нижче 30, тропічний з 70. Покриви в порядку перевірки: тундра
     * (полярний), болото (вологий рівнинний низ: волога ≥ 80, висота ≤ 30), пустеля (посушливий, волога ≤ 15), ліс
     * (волога ≥ 55).
     */
    public static final ClimateDef CLIMATE =
            climate(List.of(world("cold", 1, -10), world("temperate", 2, 0), world("warm", 1, 10)));

    /** Море — від 5 комірок, зони по 20 комірок. */
    public static final SeaDef SEA = new SeaDef(5, 20);

    /** Річка — від стоку 300 (басейн щонайменше з кількох вологих комірок) і від двох комірок у системі. */
    public static final RiverDef RIVERS = new RiverDef(300, 2);

    /**
     * Родючість: основа полярний 0 / бореальний 20 / помірний 60 / посушливий 10 / тропічний 40; рівнина +10, пагорби 0,
     * гори −40, ліс −10, пустеля −30, тундра −20, болото −20; +20% вологи; річка +20.
     */
    public static final FertilityDef FERTILITY = fertility(20);

    /** Три рівні площі: половина, середня, подвійна; мінімум — 2 провінції. */
    public static final PlacementDef PLACEMENT = placement(2);

    public static final MapContent CONTENT = new MapContent(
            List.of(PANGAEA, ARCHIPELAGO), GRID, CONTINENTS, RELIEF, CLIMATE, SEA, RIVERS, FERTILITY, PLACEMENT);

    /** NPC: мало 0–1, звичайно 2–4, багато 10–20; 60–100 провінцій на державу з кроком 20; 5–15% нічийних; 100–3000. */
    public static final WorldBalanceDef BALANCE = world(new CountRange(100, 3000));

    private TestMaps() {}

    /** Шаблон із суходолом 50%. */
    public static MapTemplateDef template(
            String id, int weight, int provincesPct, int minContinents, int maxContinents) {
        return template(id, weight, provincesPct, 50, minContinents, maxContinents);
    }

    public static MapTemplateDef template(
            String id, int weight, int provincesPct, int landPct, int minContinents, int maxContinents) {
        return new MapTemplateDef(
                new MapTemplateId(id),
                "Шаблон " + id,
                "Опис шаблону " + id,
                weight,
                provincesPct,
                landPct,
                new CountRange(minContinents, maxContinents));
    }

    /** Контент {@link #CONTENT} з іншими шаблонами й числами материків. */
    public static MapContent content(List<MapTemplateDef> templates, ContinentsDef continents) {
        return new MapContent(templates, GRID, continents, RELIEF, CLIMATE, SEA, RIVERS, FERTILITY, PLACEMENT);
    }

    /** Контент {@link #CONTENT} з іншим рельєфом. */
    public static MapContent content(ReliefDef relief) {
        return new MapContent(
                List.of(PANGAEA, ARCHIPELAGO), GRID, CONTINENTS, relief, CLIMATE, SEA, RIVERS, FERTILITY, PLACEMENT);
    }

    /** Контент {@link #CONTENT} з іншим кліматом. */
    public static MapContent content(ClimateDef climate) {
        return new MapContent(
                List.of(PANGAEA, ARCHIPELAGO), GRID, CONTINENTS, RELIEF, climate, SEA, RIVERS, FERTILITY, PLACEMENT);
    }

    /** Контент {@link #CONTENT} з іншими числами моря. */
    public static MapContent content(SeaDef sea) {
        return new MapContent(
                List.of(PANGAEA, ARCHIPELAGO), GRID, CONTINENTS, RELIEF, CLIMATE, sea, RIVERS, FERTILITY, PLACEMENT);
    }

    /** Контент {@link #CONTENT} з іншими числами річок. */
    public static MapContent content(RiverDef rivers) {
        return new MapContent(
                List.of(PANGAEA, ARCHIPELAGO), GRID, CONTINENTS, RELIEF, CLIMATE, SEA, rivers, FERTILITY, PLACEMENT);
    }

    /** Контент {@link #CONTENT} з іншою родючістю. */
    public static MapContent content(FertilityDef fertility) {
        return new MapContent(
                List.of(PANGAEA, ARCHIPELAGO), GRID, CONTINENTS, RELIEF, CLIMATE, SEA, RIVERS, fertility, PLACEMENT);
    }

    public static MapContent content(PlacementDef placement) {
        return new MapContent(
                List.of(PANGAEA, ARCHIPELAGO), GRID, CONTINENTS, RELIEF, CLIMATE, SEA, RIVERS, FERTILITY, placement);
    }

    /** Розміщення {@link #PLACEMENT} з іншим мінімумом провінцій держави. */
    public static PlacementDef placement(int minProvinces) {
        return new PlacementDef(
                List.of(area("small", 50, 30, 20), area("medium", 100, 50, 50), area("large", 200, 20, 80)),
                minProvinces,
                50,
                3);
    }

    /** Рівень площі з міткою {@code <id>_country}. */
    public static AreaLevelDef area(String id, int sharePct, int weight, int quality) {
        return new AreaLevelDef(
                new AreaLevelId(id),
                "Площа " + id,
                "Опис площі " + id,
                sharePct,
                weight,
                quality,
                List.of(id + "_country"));
    }

    /** Родючість {@link #FERTILITY} з іншим бонусом річки. */
    public static FertilityDef fertility(int river) {
        TreeMap<Climate, Integer> climates = new TreeMap<>();
        climates.put(Climate.POLAR, 0);
        climates.put(Climate.BOREAL, 20);
        climates.put(Climate.TEMPERATE, 60);
        climates.put(Climate.ARID, 10);
        climates.put(Climate.TROPICAL, 40);
        TreeMap<Terrain, Integer> terrains = new TreeMap<>();
        terrains.put(Terrain.PLAIN, 10);
        terrains.put(Terrain.HILLS, 0);
        terrains.put(Terrain.MOUNTAINS, -40);
        terrains.put(Terrain.FOREST, -10);
        terrains.put(Terrain.DESERT, -30);
        terrains.put(Terrain.TUNDRA, -20);
        terrains.put(Terrain.SWAMP, -20);
        return new FertilityDef(climates, terrains, 20, river);
    }

    /** Клімат {@link #CLIMATE} з іншими кліматами світу. */
    public static ClimateDef climate(List<WorldClimateDef> worlds) {
        return climate(
                worlds,
                List.of(
                        cover(Cover.TUNDRA, List.of(Climate.POLAR), List.of(Relief.PLAIN, Relief.HILLS), 0, 100, 100),
                        cover(Cover.SWAMP, WET, List.of(Relief.PLAIN), 80, 100, 30),
                        cover(Cover.DESERT, List.of(Climate.ARID), List.of(Relief.PLAIN, Relief.HILLS), 0, 15, 100),
                        cover(Cover.FOREST, WET, List.of(Relief.PLAIN, Relief.HILLS), 55, 100, 100)));
    }

    /** Клімат {@link #CLIMATE} з іншими кліматами світу й покривами. */
    public static ClimateDef climate(List<WorldClimateDef> worlds, List<CoverDef> covers) {
        return new ClimateDef(
                worlds,
                new ClimateTemperatureDef(90, 0, 30, 10),
                new ClimateMoistureDef(80, 10, 20),
                3,
                15,
                35,
                70,
                30,
                zones(),
                covers);
    }

    public static WorldClimateDef world(String id, int weight, int shift) {
        return new WorldClimateDef(
                new WorldClimateId(id), "Клімат світу " + id, "Опис клімату світу " + id, weight, shift);
    }

    /** Пояси в порядку {@link Climate}. */
    public static List<ClimateZoneDef> zones() {
        return Arrays.stream(Climate.values())
                .map(climate -> new ClimateZoneDef(climate, "Пояс " + climate.key(), "Опис поясу " + climate.key()))
                .toList();
    }

    /** Покрив з вологою {@code minMoisture..maxMoisture} і висотою до {@code maxHeight}. */
    public static CoverDef cover(
            Cover cover,
            List<Climate> climates,
            List<Relief> reliefs,
            int minMoisture,
            int maxMoisture,
            int maxHeight) {
        return new CoverDef(
                cover,
                "Покрив " + cover.key(),
                "Опис покриву " + cover.key(),
                climates,
                reliefs,
                new CountRange(minMoisture, maxMoisture),
                new CountRange(0, maxHeight));
    }

    /** Рельєф {@link #RELIEF} з іншою кількістю хребтів і порогами. */
    public static ReliefDef relief(CountRange ridges, int ridgeMinProvinces, int hillsFrom, int mountainsFrom) {
        return new ReliefDef(
                ridges,
                ridgeMinProvinces,
                100,
                20,
                60,
                20,
                20,
                20,
                3,
                List.of(
                        level(Relief.PLAIN, 0),
                        level(Relief.HILLS, hillsFrom),
                        level(Relief.MOUNTAINS, mountainsFrom)));
    }

    public static ReliefLevelDef level(Relief relief, int minHeight) {
        return new ReliefLevelDef(relief, "Рельєф " + relief.key(), "Опис рельєфу " + relief.key(), minHeight);
    }

    /** Баланс {@link #BALANCE} з іншими межами кількості провінцій. */
    public static WorldBalanceDef world(CountRange provinces) {
        TreeMap<NpcShare, CountRange> npc = new TreeMap<>();
        npc.put(NpcShare.FEW, new CountRange(0, 1));
        npc.put(NpcShare.NORMAL, new CountRange(2, 4));
        npc.put(NpcShare.MANY, new CountRange(10, 20));
        return new WorldBalanceDef(npc, new StepRange(60, 100, 20), new StepRange(500, 1500, 500), provinces);
    }
}
