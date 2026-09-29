package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.OutcomeTier;

/**
 * Контент для тестів колеса ВВП: п'ять рівнів від злиднів до багатства (сума ваг — рівно 10 000, тож ваги контенту
 * й є базисними пунктами) і лад, що тягне ВВП вгору.
 */
final class TestGdp {

    static final String[] IDS = {"destitute", "poor", "middle", "rich", "very_rich"};
    static final int[] PER_CAPITA = {100, 300, 1000, 2500, 5000};
    static final OutcomeTier[] TIERS = OutcomeTier.values();
    static final int[] WEIGHTS = {1000, 2000, 4000, 2000, 1000};
    static final int[] QUALITIES = {5, 25, 50, 75, 95};

    /** Перевага за кожен рівень економіки й за кожен рівень суспільства. */
    static final int DEVELOPMENT_ADVANTAGE = 10;

    /** Лад: +20 від ідеології, ще +10 від підкласифікації. */
    static final IdeologyDef DEMOCRACY = new IdeologyDef(
            new IdeologyId("democracy"),
            "Демократія",
            100,
            List.of(new ModifierDef(ModifierTarget.wheel(GdpWheel.KIND), 20)),
            List.of("democratic"),
            List.of(new SubIdeologyDef(
                    new SubIdeologyId("liberal_democracy"),
                    "Ліберальна демократія",
                    100,
                    List.of(new ModifierDef(ModifierTarget.wheel(GdpWheel.KIND), 10)),
                    List.of("liberal"))));

    static final Regime REGIME = new Regime(
            DEMOCRACY,
            DEMOCRACY.subIdeologies().getFirst(),
            new TreeSet<>(List.of("democratic", "liberal")),
            List.of());

    static final List<GdpLevelDef> LEVELS = levels();

    static final ContentPack PACK = TestBackstory.pack(
            List.of(DEMOCRACY),
            TestBackstory.FRAGMENTS,
            TestBackstory.COUNT,
            TestDevelopment.LEVELS,
            List.of(new ResourceDef(new ResourceId("iron"), "Залізо", List.of())),
            List.of(
                    new NuclearStatusDef(NuclearStatus.NONE, "Немає", 80, 50, List.of()),
                    new NuclearStatusDef(NuclearStatus.PROGRAM, "Програма", 13, 70, List.of()),
                    new NuclearStatusDef(NuclearStatus.ARSENAL, "Арсенал", 7, 90, List.of())),
            new CountRange(2, 10),
            10,
            LEVELS,
            DEVELOPMENT_ADVANTAGE);

    private TestGdp() {}

    /** Економіка й суспільство — на заданих рівнях, решта галузей — на світовому. */
    static StartDevelopment development(int economy, int society) {
        EnumMap<TechBranch, Integer> levels = new EnumMap<>(TechBranch.class);
        for (TechBranch branch : TechBranch.values()) {
            levels.put(branch, 0);
        }
        levels.put(TechBranch.ECONOMY, economy);
        levels.put(TechBranch.SOCIETY, society);
        return new StartDevelopment(levels, new TreeSet<>(), 50, List.of());
    }

    /** Модифікатор переваги колеса ВВП від довільної події. */
    static Modifier modifier(String id, int value) {
        return new ModifierDef(ModifierTarget.wheel(GdpWheel.KIND), value)
                .toModifier(id, new ModifierSource(SourceKind.EVENT, "test"), null, "event.test");
    }

    private static List<GdpLevelDef> levels() {
        List<GdpLevelDef> levels = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            List<String> tags = i < 2 ? List.of("poor") : i > 2 ? List.of("rich") : List.of();
            levels.add(new GdpLevelDef(
                    new GdpLevelId(IDS[i]),
                    "Рівень " + IDS[i],
                    "Опис " + IDS[i],
                    PER_CAPITA[i],
                    TIERS[i],
                    WEIGHTS[i],
                    QUALITIES[i],
                    tags));
        }
        return List.copyOf(levels);
    }
}
