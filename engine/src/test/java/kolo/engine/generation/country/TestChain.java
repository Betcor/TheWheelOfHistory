package kolo.engine.generation.country;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.BackstoryContent;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.BackstoryText;
import kolo.engine.content.BalanceDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.content.DoctrineDef;
import kolo.engine.content.DoctrineId;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.GenerationBalanceDef;
import kolo.engine.content.HdiLevelDef;
import kolo.engine.content.HdiLevelId;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.MapContent;
import kolo.engine.content.MedianRange;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.PersonKindDef;
import kolo.engine.content.PlacementDef;
import kolo.engine.content.PopulationDef;
import kolo.engine.content.PopulationLevelDef;
import kolo.engine.content.PowerCorridorDef;
import kolo.engine.content.StreakContent;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.content.StreakRewardId;
import kolo.engine.content.StreakRulesDef;
import kolo.engine.content.StreakWheelDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TagCondition;
import kolo.engine.content.TechBranchDef;
import kolo.engine.content.TestMaps;
import kolo.engine.content.TestPower;
import kolo.engine.content.TestReligions;
import kolo.engine.content.TestResources;
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.content.WheelBalanceDef;
import kolo.engine.generation.map.MapGenerator;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.rng.Rng;
import kolo.engine.state.Development;
import kolo.engine.state.NpcShare;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.Stat;
import kolo.engine.state.TechBranch;
import kolo.engine.state.Training;
import kolo.engine.wheel.OutcomeTier;

/**
 * Контент для тестів ланцюжка коліс генерації. Усі рівні кожного колеса мають однакову якість, тож стріки
 * передбачувані:
 *
 * <ul>
 *   <li>{@link #NEUTRAL} — якість 50 усюди, стріків немає;
 *   <li>{@link #STREAKY} — розвиненість, ВВП та ІЛР дуже добрі («Золота доба» після ІЛР), армія, вишкіл і ядерний
 *       статус дуже погані («Андердог» після ядерного статусу);
 *   <li>{@link #MAP_STREAKY} — площа, населення й розвиненість дуже добрі («Золота доба» після розвиненості), решта
 *       нейтральна.
 * </ul>
 *
 * <p>Карта — {@link TestMaps#CONTENT} з рівнями площі й населення потрібної якості, ресурси — {@link
 * TestResources#RESOURCES}; населення дає ±5 розвиненості й ∓5 ВВП за крок рівня. Світ для пакета генерується один
 * раз ({@link #world}).
 *
 * <p>«Золота доба» дає постать, 2 жетони й перевагу колесу розміру армії; «Андердог» — ще 2 жетони й стабільність на
 * 10 років. Фрагмент {@link #GLORY} доступний лише з міткою {@code golden_age} і має модифікатори на 5 років.
 */
final class TestChain {

    static final int GOOD = 90;
    static final int BAD = 10;
    static final int NEUTRAL_QUALITY = 50;

    /** Перевага колеса розміру армії від «Золотої доби». */
    static final int ARMY_ADVANTAGE = 20;

    static final StreakRewardDef GREAT_FIGURE = new StreakRewardDef(
            new StreakRewardId("great_figure"),
            "Видатна постать",
            "Опис",
            100,
            0,
            List.of(new ModifierDef(ModifierTarget.wheel(ArmySizeWheel.KIND), ARMY_ADVANTAGE)),
            2,
            1,
            List.of());

    static final StreakRewardDef SECOND_CHANCE = new StreakRewardDef(
            new StreakRewardId("second_chance"),
            "Другий шанс",
            "Опис",
            100,
            10,
            List.of(new ModifierDef(ModifierTarget.stat(Stat.STABILITY), 10)),
            2,
            0,
            List.of("sympathy"));

    static final StreakContent STREAKS = new StreakContent(List.of(
            new StreakWheelDef(
                    StreakKind.GOLDEN_AGE,
                    "Золота доба",
                    "Опис",
                    List.of("golden_age", "world_attention"),
                    List.of(GREAT_FIGURE)),
            new StreakWheelDef(StreakKind.UNDERDOG, "Андердог", "Опис", List.of("underdog"), List.of(SECOND_CHANCE))));

    /** Лише після «Золотої доби»; два модифікатори на 5 років. */
    static final BackstoryFragmentDef GLORY = new BackstoryFragmentDef(
            new BackstoryFragmentId("glory"),
            10_000,
            NEUTRAL_QUALITY,
            1950,
            1969,
            new TagCondition(List.of("golden_age"), List.of(), List.of()),
            new TreeMap<>(),
            false,
            List.of("glorious"),
            5,
            List.of(
                    new ModifierDef(ModifierTarget.stat(Stat.INFLUENCE), 5),
                    new ModifierDef(ModifierTarget.stat(Stat.LEGITIMACY), 5)),
            new BackstoryText("text", "Слава {country.genitive} {year} року."));

    static final List<BackstoryFragmentDef> FRAGMENTS = List.of(
            GLORY,
            plain("flood", 1900, 1969),
            plain("famine", 1900, 1969),
            plain("reform", 1900, 1969),
            new BackstoryFragmentDef(
                    new BackstoryFragmentId("old_feud"),
                    100,
                    NEUTRAL_QUALITY,
                    1920,
                    1960,
                    TagCondition.NONE,
                    new TreeMap<>(),
                    true,
                    List.of("old_feud"),
                    0,
                    List.of(),
                    new BackstoryText("text", "Ворожнеча з {neighbor.instrumental} {year} року.")));

    /** Перевага розвиненості за крок рівня населення. */
    static final int DEVELOPMENT_PER_STEP = 5;

    /** Перевага ВВП за крок рівня населення. */
    static final int GDP_PER_STEP = -5;

    /** Seed карти світу: один материк на п'ять держав, у кожної є сусіди по суходолу. */
    static final long WORLD_SEED = 1961;

    static final ContentPack NEUTRAL = pack(NEUTRAL_QUALITY, NEUTRAL_QUALITY, NEUTRAL_QUALITY);
    static final ContentPack STREAKY = pack(GOOD, BAD, NEUTRAL_QUALITY);
    static final ContentPack MAP_STREAKY = pack(GOOD, NEUTRAL_QUALITY, GOOD);

    private static final Map<ContentPack, WorldMap> WORLDS = new ConcurrentHashMap<>();

    private TestChain() {}

    /** Релігії світу для пакета; кількість — як для кількох держав. */
    static List<StartReligion> religions(ContentPack pack) {
        return WorldReligionsWheel.generate(Rng.of(7), pack, 4).religions();
    }

    /** Карта світу пакета: один гравець, звичайна частка NPC, seed {@link #WORLD_SEED}. */
    static WorldMap world(ContentPack pack) {
        return WORLDS.computeIfAbsent(
                pack, key -> MapGenerator.generate(Rng.of(WORLD_SEED), key, WorldSizeInput.of(1, NpcShare.NORMAL)));
    }

    /** Держава 0 світу пакета з його релігіями, без зайнятих назв. */
    static CountryGenerationInput input(ContentPack pack) {
        return input(pack, 0);
    }

    static CountryGenerationInput input(ContentPack pack, int country) {
        return CountryGenerationInput.of(world(pack), country, religions(pack));
    }

    /**
     * @param early якість кожного рівня розвиненості, ВВП та ІЛР
     * @param late якість кожного рівня розміру й вишколу армії та ядерного статусу
     * @param map якість кожного рівня площі й населення
     */
    static ContentPack pack(int early, int late, int map) {
        return new ContentPack(
                "0".repeat(64),
                List.of(
                        ideology("democracy", "liberal_democracy", "direct_democracy"),
                        ideology("monarchy", "absolute_monarchy")),
                List.of(new DoctrineDef(new DoctrineId("armored"), "Бронетанкова", List.of(), List.of())),
                TestResources.RESOURCES,
                Arrays.stream(TechBranch.values())
                        .map(branch -> new TechBranchDef(branch, "Галузь"))
                        .toList(),
                Development.levels().stream()
                        .map(level -> new DevelopmentLevelDef(level, "Рівень", "Опис", 100, early, List.of()))
                        .toList(),
                Arrays.stream(NuclearStatus.values())
                        .map(status -> new NuclearStatusDef(status, "Статус", 100, late, List.of()))
                        .toList(),
                List.of(
                        new GdpLevelDef(
                                new GdpLevelId("poor"), "Рівень", "Опис", 300, OutcomeTier.FAIL, 100, early, List.of()),
                        new GdpLevelDef(
                                new GdpLevelId("rich"),
                                "Рівень",
                                "Опис",
                                3000,
                                OutcomeTier.SUCCESS,
                                100,
                                early,
                                List.of())),
                List.of(new HdiLevelDef(
                        new HdiLevelId("middle"), "Рівень", "Опис", 60, OutcomeTier.PARTIAL, 100, early, List.of())),
                List.of(
                        new ArmySizeDef(
                                new ArmySizeId("small"), "Рівень", "Опис", 50, OutcomeTier.FAIL, 100, late, List.of()),
                        new ArmySizeDef(
                                new ArmySizeId("large"),
                                "Рівень",
                                "Опис",
                                400,
                                OutcomeTier.SUCCESS,
                                100,
                                late,
                                List.of())),
                Training.levels().stream()
                        .map(level -> new TrainingLevelDef(
                                level, "Рівень", "Опис", level * 10, OutcomeTier.PARTIAL, 100, late, List.of()))
                        .toList(),
                Arrays.stream(PersonKind.values())
                        .map(kind ->
                                new PersonKindDef(kind, "Тип", "Опис", 10, Collections.emptySortedMap(), List.of()))
                        .toList(),
                List.of(new TraitDef(new TraitId("loyal"), "Відданий", List.of(), List.of(), List.of(), List.of())),
                TestNames.northernNameContent(),
                new BackstoryContent(Map.of(), FRAGMENTS),
                STREAKS,
                TestReligions.content(),
                map(map),
                BalanceDef.of(
                        new WheelBalanceDef(50, new TreeMap<>(), List.of(10)),
                        new StreakRulesDef(85, 15, 3),
                        Arrays.stream(PowerCorridor.values())
                                .map(corridor -> new PowerCorridorDef(
                                        corridor, new MedianRange(50, 200), new MedianRange(50, 200)))
                                .toList(),
                        new GenerationBalanceDef(
                                new CountRange(2, 3),
                                new CountRange(1, 3),
                                new CountRange(2, 10),
                                10,
                                10,
                                15,
                                10,
                                10,
                                10,
                                new CountRange(1, 1),
                                new CountRange(25, 70),
                                5,
                                DEVELOPMENT_PER_STEP,
                                GDP_PER_STEP),
                        TestReligions.BALANCE,
                        TestMaps.BALANCE,
                        TestResources.BALANCE,
                        TestPower.BUDGET));
    }

    /** {@link TestMaps#CONTENT} з рівнями площі й населення якості {@code quality}. */
    private static MapContent map(int quality) {
        PlacementDef placement = new PlacementDef(
                List.of(
                        TestMaps.area("small", 50, 30, quality),
                        TestMaps.area("medium", 100, 50, quality),
                        TestMaps.area("large", 200, 20, quality)),
                TestMaps.PLACEMENT.minProvinces(),
                TestMaps.PLACEMENT.roughness(),
                TestMaps.PLACEMENT.noiseCells());
        List<PopulationLevelDef> levels = TestMaps.POPULATION.levels().stream()
                .map(level -> new PopulationLevelDef(
                        level.id(),
                        level.name(),
                        level.description(),
                        level.populationK(),
                        level.tier(),
                        level.weight(),
                        quality,
                        level.tags()))
                .toList();
        PopulationDef population = new PopulationDef(
                levels,
                TestMaps.POPULATION.areaAdvantage(),
                TestMaps.POPULATION.fertilityAdvantage(),
                TestMaps.POPULATION.provinceBase(),
                TestMaps.POPULATION.coastBonus());
        MapContent base = TestMaps.CONTENT;
        return new MapContent(
                base.templates(),
                base.grid(),
                base.continents(),
                base.relief(),
                base.climate(),
                base.sea(),
                base.rivers(),
                base.fertility(),
                placement,
                base.geography(),
                population);
    }

    private static IdeologyDef ideology(String id, String... subs) {
        return new IdeologyDef(
                new IdeologyId(id),
                "Ідеологія " + id,
                100,
                List.of(),
                List.of(id),
                Arrays.stream(subs)
                        .map(sub -> new SubIdeologyDef(
                                new SubIdeologyId(sub), "Підкласифікація " + sub, 100, List.of(), List.of()))
                        .toList());
    }

    private static BackstoryFragmentDef plain(String id, int from, int to) {
        return new BackstoryFragmentDef(
                new BackstoryFragmentId(id),
                100,
                NEUTRAL_QUALITY,
                from,
                to,
                TagCondition.NONE,
                new TreeMap<>(),
                false,
                List.of(id),
                0,
                List.of(),
                new BackstoryText("text", "Подія " + id + " {year} року."));
    }
}
