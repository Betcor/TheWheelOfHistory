package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
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
 * Контент для тестів колеса розміру армії: п'ять рівнів від майже відсутньої армії до всієї нації під зброєю (сума
 * ваг — рівно 10 000, тож ваги контенту й є базисними пунктами) і лад, що тягне армію вгору.
 */
final class TestArmy {

    static final String[] IDS = {"almost_none", "small", "regular", "large", "nation_in_arms"};
    static final int[] SHARES_BP = {20, 40, 150, 300, 800};
    static final OutcomeTier[] TIERS = OutcomeTier.values();
    static final int[] WEIGHTS = {1000, 2000, 4000, 2000, 1000};
    static final int[] QUALITIES = {15, 30, 50, 60, 80};

    /** Перевага за кожен крок рівня результату рівня ВВП. */
    static final int GDP_ADVANTAGE = 10;

    /** Лад: +10 від ідеології, ще +15 від підкласифікації. */
    static final IdeologyDef TOTALITARIANISM = new IdeologyDef(
            new IdeologyId("totalitarianism"),
            "Тоталітаризм",
            100,
            List.of(new ModifierDef(ModifierTarget.wheel(ArmySizeWheel.KIND), 10)),
            List.of("totalitarian"),
            List.of(new SubIdeologyDef(
                    new SubIdeologyId("militarism"),
                    "Мілітаризм",
                    100,
                    List.of(new ModifierDef(ModifierTarget.wheel(ArmySizeWheel.KIND), 15)),
                    List.of("militarism"))));

    static final Regime REGIME = new Regime(
            TOTALITARIANISM,
            TOTALITARIANISM.subIdeologies().getFirst(),
            new TreeSet<>(List.of("militarism", "totalitarian")),
            List.of());

    static final List<ArmySizeDef> SIZES = sizes();

    static final ContentPack PACK = TestBackstory.pack(
            List.of(TOTALITARIANISM),
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
            TestHdi.LEVELS,
            TestHdi.GDP_ADVANTAGE,
            SIZES,
            GDP_ADVANTAGE);

    private TestArmy() {}

    /** Модифікатор переваги колеса розміру армії від довільної події. */
    static Modifier modifier(String id, int value) {
        return new ModifierDef(ModifierTarget.wheel(ArmySizeWheel.KIND), value)
                .toModifier(id, new ModifierSource(SourceKind.EVENT, "test"), null, "event.test");
    }

    private static List<ArmySizeDef> sizes() {
        List<ArmySizeDef> sizes = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            List<String> tags = i < 2 ? List.of("small_army") : i > 2 ? List.of("large_army") : List.of();
            sizes.add(new ArmySizeDef(
                    new ArmySizeId(IDS[i]),
                    "Рівень " + IDS[i],
                    "Опис " + IDS[i],
                    SHARES_BP[i],
                    TIERS[i],
                    WEIGHTS[i],
                    QUALITIES[i],
                    tags));
        }
        return List.copyOf(sizes);
    }
}
