package kolo.engine.generation.country;

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
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.TechBranch;

/**
 * Контент для тестів колеса ядерного статусу: ваги статусів за GD §4.6 (сума — рівно 10 000, тож ваги контенту й є
 * базисними пунктами), уран з міткою {@code nuclear_fuel}, залізо без неї й лад, що тягне до ядерної зброї.
 */
final class TestNuclear {

    /** Ваги статусів у порядку {@link NuclearStatus}: немає, програма, арсенал. */
    static final int[] WEIGHTS = {8000, 1300, 700};

    static final int[] QUALITIES = {50, 70, 90};

    static final ResourceId IRON = new ResourceId("iron");
    static final ResourceId URANIUM = new ResourceId("uranium");

    static final CountRange WARHEADS = new CountRange(2, 5);

    /** Перевага за кожен рівень енергетики й науки. */
    static final int ENERGY_ADVANTAGE = 10;

    /** Лад: +20 від ідеології, ще +10 від підкласифікації. */
    static final IdeologyDef TOTALITARIANISM = new IdeologyDef(
            new IdeologyId("totalitarianism"),
            "Тоталітаризм",
            100,
            List.of(new ModifierDef(ModifierTarget.wheel(NuclearWheel.KIND), 20)),
            List.of("totalitarian"),
            List.of(new SubIdeologyDef(
                    new SubIdeologyId("militarism"),
                    "Мілітаризм",
                    100,
                    List.of(new ModifierDef(ModifierTarget.wheel(NuclearWheel.KIND), 10)),
                    List.of("militarism"))));

    static final Regime REGIME = new Regime(
            TOTALITARIANISM,
            TOTALITARIANISM.subIdeologies().getFirst(),
            new TreeSet<>(List.of("totalitarian", "militarism")),
            List.of());

    static final ContentPack PACK = TestBackstory.pack(
            List.of(TOTALITARIANISM),
            TestBackstory.FRAGMENTS,
            TestBackstory.COUNT,
            TestDevelopment.LEVELS,
            List.of(
                    new ResourceDef(IRON, "Залізо", List.of("metal")),
                    new ResourceDef(URANIUM, "Уран", List.of("energy", NuclearWheel.NUCLEAR_FUEL_TAG))),
            List.of(
                    status(NuclearStatus.NONE, List.of()),
                    status(NuclearStatus.PROGRAM, List.of("nuclear_program")),
                    status(NuclearStatus.ARSENAL, List.of("nuclear_power"))),
            WARHEADS,
            ENERGY_ADVANTAGE);

    private TestNuclear() {}

    /** Усі галузі на світовому рівні, енергетика й наука — на {@code energy}. */
    static StartDevelopment development(int energy) {
        EnumMap<TechBranch, Integer> levels = new EnumMap<>(TechBranch.class);
        for (TechBranch branch : TechBranch.values()) {
            levels.put(branch, 0);
        }
        levels.put(TechBranch.ENERGY_SCIENCE, energy);
        return new StartDevelopment(levels, new TreeSet<>(), 50, List.of());
    }

    /** Модифікатор переваги колеса ядерного статусу від довільної події. */
    static Modifier modifier(String id, int value) {
        return new ModifierDef(ModifierTarget.wheel(NuclearWheel.KIND), value)
                .toModifier(id, new ModifierSource(SourceKind.EVENT, "test"), null, "event.test");
    }

    private static NuclearStatusDef status(NuclearStatus status, List<String> tags) {
        int index = status.ordinal();
        return new NuclearStatusDef(status, "Статус " + status.key(), WEIGHTS[index], QUALITIES[index], tags);
    }
}
