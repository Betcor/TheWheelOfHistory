package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.TreeSet;
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
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.OutcomeTier;

/**
 * Контент для тестів колеса вишколу армії: п'ять рівнів від ополчення до еліти (сума ваг — рівно 10 000, тож ваги
 * контенту й є базисними пунктами) і лад, що тягне вишкіл угору.
 */
final class TestTraining {

    static final String[] IDS = {"level_1", "level_2", "level_3", "level_4", "level_5"};
    static final int[] COMBAT_MODIFIERS = {-20, -10, 0, 10, 20};
    static final OutcomeTier[] TIERS = OutcomeTier.values();
    static final int[] WEIGHTS = {1000, 2000, 4000, 2000, 1000};
    static final int[] QUALITIES = {10, 30, 50, 70, 90};

    /** Перевага за кожен крок рівня результату рівня ВВП. */
    static final int GDP_ADVANTAGE = 10;

    /** Перевага за кожен рівень розвиненості військової галузі. */
    static final int DEVELOPMENT_ADVANTAGE = 10;

    /** Лад: +5 від ідеології, ще +10 від підкласифікації. */
    static final IdeologyDef AUTHORITARIANISM = new IdeologyDef(
            new IdeologyId("authoritarianism"),
            "Авторитаризм",
            100,
            List.of(new ModifierDef(ModifierTarget.wheel(ArmyTrainingWheel.KIND), 5)),
            List.of("authoritarian"),
            List.of(new SubIdeologyDef(
                    new SubIdeologyId("military_junta"),
                    "Військова хунта",
                    100,
                    List.of(new ModifierDef(ModifierTarget.wheel(ArmyTrainingWheel.KIND), 10)),
                    List.of("junta"))));

    static final Regime REGIME = new Regime(
            AUTHORITARIANISM,
            AUTHORITARIANISM.subIdeologies().getFirst(),
            new TreeSet<>(List.of("authoritarian", "junta")),
            List.of());

    static final List<TrainingLevelDef> LEVELS = levels();

    static final ContentPack PACK = TestBackstory.pack(
            List.of(AUTHORITARIANISM),
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
            TestArmy.SIZES,
            TestArmy.GDP_ADVANTAGE,
            LEVELS,
            GDP_ADVANTAGE,
            DEVELOPMENT_ADVANTAGE);

    private TestTraining() {}

    /** Військова галузь — на заданому рівні, решта — на світовому. */
    static StartDevelopment development(int military) {
        EnumMap<TechBranch, Integer> levels = new EnumMap<>(TechBranch.class);
        for (TechBranch branch : TechBranch.values()) {
            levels.put(branch, 0);
        }
        levels.put(TechBranch.MILITARY, military);
        return new StartDevelopment(levels, new TreeSet<>(), 50, List.of());
    }

    /** Модифікатор переваги колеса вишколу від довільної події. */
    static Modifier modifier(String id, int value) {
        return new ModifierDef(ModifierTarget.wheel(ArmyTrainingWheel.KIND), value)
                .toModifier(id, new ModifierSource(SourceKind.EVENT, "test"), null, "event.test");
    }

    private static List<TrainingLevelDef> levels() {
        List<TrainingLevelDef> levels = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            List<String> tags = i == IDS.length - 1 ? List.of("elite_army") : List.of();
            levels.add(new TrainingLevelDef(
                    i + 1,
                    "Рівень " + (i + 1),
                    "Опис " + (i + 1),
                    COMBAT_MODIFIERS[i],
                    TIERS[i],
                    WEIGHTS[i],
                    QUALITIES[i],
                    tags));
        }
        return List.copyOf(levels);
    }
}
