package kolo.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
import kolo.engine.content.PopulationLevelId;
import kolo.engine.content.ReligionPolityId;
import kolo.engine.content.ResourceId;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TraitId;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.CellKind;
import kolo.engine.state.Climate;
import kolo.engine.state.CountryId;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.GridPoint;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.ProvinceId;
import kolo.engine.state.Relief;
import kolo.engine.state.Religion;
import kolo.engine.state.ReligionId;
import kolo.engine.state.Season;
import kolo.engine.state.Sex;
import kolo.engine.state.Stat;
import kolo.engine.state.TechBranch;
import kolo.engine.state.Terrain;
import kolo.engine.view.CellView;
import kolo.engine.view.CountryCard;
import kolo.engine.view.CountryView;
import kolo.engine.view.MapView;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.WheelKind;

/**
 * Карти для тестів протоколу: смуга квадратних комірок, у якій трапляються всі варіанти полів — суходіл з річкою й без,
 * нічийна земля, море й озеро, дві держави з українськими назвами (кирилиця й апостроф на дроті).
 */
public final class TestMessages {

    public static final String HASH = "a".repeat(64);

    /** Ключ світу. */
    public static final String WORLD = "0123456789abcdef0123456789abcdef";

    private static final int SIDE = 10;

    private TestMessages() {}

    /** Карта з {@code cells} комірок (щонайменше одна). */
    public static MapView map(long seed, int cells) {
        List<CellView> views = new ArrayList<>(cells);
        for (int n = 0; n < cells; n++) {
            views.add(cell(n, cells));
        }
        List<CountryView> countries = List.of(
                new CountryView(0, name("Республіка Вел'ор"), true, 1), new CountryView(1, name("Орін"), false, 1));
        return new MapView(seed, SIDE * cells, SIDE, views, countries);
    }

    /**
     * Картка держави {@code number}. Парна — з релігією, сусідом передісторії, стріком і колесом із сезоном; непарна —
     * світська, без них; модифікатори — обох цілей, з терміном і без.
     */
    public static CountryCard card(int number, int seed) {
        boolean full = number % 2 == 0;
        Optional<Religion> religion = full
                ? Optional.of(new Religion(
                        ReligionId.of(3),
                        new ArchetypeId("dualism"),
                        List.of(new AspectId("war"), new AspectId("craft")),
                        List.of(new DogmaId("omens")),
                        new ReligionPolityId("communities"),
                        new FaithFormId("faith"),
                        Sex.FEMALE,
                        name("Каніма").fullName(),
                        name("Віра Каніми").fullName(),
                        new TreeSet<>(List.of("religion_war")),
                        ProvinceId.of(391)))
                : Optional.empty();
        CountryOrigin origin = new CountryOrigin(
                new AreaLevelId("large_country"),
                new PopulationLevelId("moderate"),
                new GdpLevelId("middle_income"),
                new HdiLevelId("high"),
                new ArmySizeId("small_army"),
                full
                        ? List.of(new CountryOrigin.Backstory(new BackstoryFragmentId("hyperinflation"), 1929))
                        : List.of(),
                full ? Optional.of(CountryId.of(4)) : Optional.empty(),
                full
                        ? List.of(new CountryOrigin.Streak(StreakKind.GOLDEN_AGE, new StreakRewardId("fate_token")))
                        : List.of(),
                PowerCorridor.CLASSIC,
                112);
        List<Modifier> modifiers = List.of(
                new Modifier(
                        "ideology:theocracy:0",
                        new ModifierSource(SourceKind.IDEOLOGY, "theocracy"),
                        ModifierTarget.stat(Stat.STABILITY),
                        10,
                        null,
                        "ideology.theocracy"),
                new Modifier(
                        "backstory:hyperinflation:0",
                        new ModifierSource(SourceKind.BACKSTORY, "hyperinflation"),
                        ModifierTarget.wheel(new WheelKind("generation_hdi")),
                        -3,
                        9,
                        "backstory.hyperinflation"));
        List<RolledSector> sectors = List.of(
                new RolledSector("small", 2500, OutcomeTier.PARTIAL, 30),
                new RolledSector("large", 7500, OutcomeTier.PARTIAL, 70));
        List<RollRecord> rolls = List.of(
                new RollRecord(
                        new WheelKind("generation_area"),
                        sectors,
                        -3,
                        List.of(new AppliedModifier("power_budget", "power.corridor", -3)),
                        "large",
                        Math.floorMod(seed, 10_000),
                        0,
                        full ? Season.WINTER : null),
                new RollRecord(new WheelKind("generation_ideology"), sectors, 0, List.of(), "small", 17, 0, null));
        return new CountryCard(
                number,
                name("Священна Держава Бренель"),
                794,
                87,
                6_000L + seed % 1000,
                new IdeologyId("theocracy"),
                new SubIdeologyId("temple_state"),
                religion,
                full ? List.of(name("Рід Батая").fullName(), name("Віра Каніми").fullName()) : List.of(),
                Map.of(
                        TechBranch.ECONOMY,
                        2,
                        TechBranch.MILITARY,
                        0,
                        TechBranch.SOCIETY,
                        -1,
                        TechBranch.ENERGY_SCIENCE,
                        1),
                1000,
                75,
                40,
                10,
                full ? NuclearStatus.ARSENAL : NuclearStatus.NONE,
                full ? 12 : 0,
                number,
                List.of(
                        new CountryCard.Deposit(794, new ResourceId("fertile_land")),
                        new CountryCard.Deposit(750, new ResourceId("coal"))),
                List.of(new CountryCard.PersonCard(
                        name("Герен Норівський"),
                        PersonKind.DIPLOMAT,
                        Sex.MALE,
                        List.of(new TraitId("hawk"), new TraitId("loyal")),
                        -44,
                        true)),
                origin,
                modifiers,
                new TreeSet<>(List.of("coastal", "theocratic")),
                rolls);
    }

    public static LocalizedName name(String text) {
        NounPhrase phrase = new NounPhrase(
                GrammaticalGender.FEMININE,
                List.of(text, text + "и", text + "і", text + "у", text + "ою", text + "і", text + "о"));
        return new LocalizedName(phrase, phrase);
    }

    private static CellView cell(int n, int cells) {
        int x = n * SIDE;
        List<GridPoint> polygon = List.of(
                new GridPoint(x, 0), new GridPoint(x + SIDE, 0), new GridPoint(x + SIDE, SIDE), new GridPoint(x, SIDE));
        List<Integer> neighbors = new ArrayList<>();
        if (n > 0) {
            neighbors.add(n - 1);
        }
        if (n + 1 < cells) {
            neighbors.add(n + 1);
        }
        GridPoint site = new GridPoint(x + SIDE / 2, SIDE / 2);
        return switch (n % 4) {
            case 0, 1 -> {
                boolean river = n % 4 == 0 && n + 1 < cells;
                yield new CellView(
                        site,
                        polygon,
                        neighbors,
                        CellKind.LAND,
                        Optional.of(n % 8 == 0 ? Terrain.FOREST : Terrain.MOUNTAINS),
                        Optional.of(Relief.HILLS),
                        Optional.of(Climate.TEMPERATE),
                        OptionalInt.of(n % 100),
                        OptionalInt.of(40),
                        river,
                        river ? OptionalInt.of(n + 1) : OptionalInt.empty(),
                        n % 8 == 0 ? OptionalInt.empty() : OptionalInt.of(n % 2));
            }
            default ->
                new CellView(
                        site,
                        polygon,
                        neighbors,
                        n % 4 == 2 ? CellKind.SEA : CellKind.LAKE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        false,
                        OptionalInt.empty(),
                        OptionalInt.empty());
        };
    }
}
