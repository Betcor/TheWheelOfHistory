package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.HdiLevelDef;
import kolo.engine.content.HdiLevelId;
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
import kolo.engine.wheel.OutcomeTier;

/**
 * Контент для тестів колеса ІЛР: п'ять рівнів від дуже низького до дуже високого (сума ваг — рівно 10 000, тож ваги
 * контенту й є базисними пунктами) і лад, що тягне ІЛР угору.
 */
final class TestHdi {

    static final String[] IDS = {"very_low", "low", "middle", "high", "very_high"};
    static final int[] HDI = {15, 35, 55, 75, 90};
    static final OutcomeTier[] TIERS = OutcomeTier.values();
    static final int[] WEIGHTS = {1000, 2000, 4000, 2000, 1000};
    static final int[] QUALITIES = {5, 25, 50, 75, 95};

    /** Перевага за кожен крок рівня результату рівня ВВП. */
    static final int GDP_ADVANTAGE = 15;

    /** Лад: +10 від ідеології, ще +10 від підкласифікації. */
    static final IdeologyDef SOCIALISM = new IdeologyDef(
            new IdeologyId("socialism"),
            "Соціалізм",
            100,
            List.of(new ModifierDef(ModifierTarget.wheel(HdiWheel.KIND), 10)),
            List.of("socialist"),
            List.of(new SubIdeologyDef(
                    new SubIdeologyId("market_socialism"),
                    "Ринковий соціалізм",
                    100,
                    List.of(new ModifierDef(ModifierTarget.wheel(HdiWheel.KIND), 10)),
                    List.of("market_socialism"))));

    static final Regime REGIME = new Regime(
            SOCIALISM,
            SOCIALISM.subIdeologies().getFirst(),
            new TreeSet<>(List.of("market_socialism", "socialist")),
            List.of());

    static final List<HdiLevelDef> LEVELS = levels();

    static final ContentPack PACK = TestBackstory.pack(
            List.of(SOCIALISM),
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
            TestGdp.LEVELS,
            TestGdp.DEVELOPMENT_ADVANTAGE,
            LEVELS,
            GDP_ADVANTAGE);

    private TestHdi() {}

    /** ВВП з цим рівнем результату; решта полів для колеса ІЛР не важлива. */
    static StartGdp gdp(OutcomeTier tier) {
        return new StartGdp(
                new GdpLevelId(TestGdp.IDS[tier.ordinal()]),
                TestGdp.PER_CAPITA[tier.ordinal()],
                tier,
                new TreeSet<>(),
                50,
                List.of());
    }

    /** Модифікатор переваги колеса ІЛР від довільної події. */
    static Modifier modifier(String id, int value) {
        return new ModifierDef(ModifierTarget.wheel(HdiWheel.KIND), value)
                .toModifier(id, new ModifierSource(SourceKind.EVENT, "test"), null, "event.test");
    }

    private static List<HdiLevelDef> levels() {
        List<HdiLevelDef> levels = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            List<String> tags = i == IDS.length - 1 ? List.of("educated") : List.of();
            levels.add(new HdiLevelDef(
                    new HdiLevelId(IDS[i]),
                    "Рівень " + IDS[i],
                    "Опис " + IDS[i],
                    HDI[i],
                    TIERS[i],
                    WEIGHTS[i],
                    QUALITIES[i],
                    tags));
        }
        return List.copyOf(levels);
    }
}
