package kolo.engine.generation.country;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.TechBranch;

/**
 * Контент для тестів колеса розвиненості: рівні з різними вагами (сума — рівно 10 000, тож ваги контенту й є
 * базисними пунктами) і лад, що тягне енергетику й науку вгору, а військо — вниз.
 */
final class TestDevelopment {

    /** Ваги рівнів −3..+2. */
    static final int[] WEIGHTS = {500, 1000, 1500, 4000, 2000, 1000};

    /** Якості рівнів −3..+2. */
    static final int[] QUALITIES = {5, 15, 35, 50, 75, 95};

    static final List<DevelopmentLevelDef> LEVELS = List.of(
            level(-3, List.of("backward")),
            level(-2, List.of("backward")),
            level(-1, List.of()),
            level(0, List.of()),
            level(1, List.of("advanced")),
            level(2, List.of("advanced", "leader")));

    /** Перевага на крайніх значеннях: +100 енергетиці й науці, −100 війську, ще +20 від підкласифікації. */
    static final IdeologyDef TECHNOCRACY = new IdeologyDef(
            new IdeologyId("authoritarianism"),
            "Авторитаризм",
            100,
            List.of(
                    wheel(TechBranch.ENERGY_SCIENCE, 100),
                    wheel(TechBranch.MILITARY, -100),
                    new ModifierDef(ModifierTarget.wheel(DevelopmentWheel.kind(TechBranch.ECONOMY)), 0)),
            List.of("authoritarian"),
            List.of(new SubIdeologyDef(
                    new SubIdeologyId("technocracy"),
                    "Технократія",
                    100,
                    List.of(wheel(TechBranch.ENERGY_SCIENCE, 20), wheel(TechBranch.ECONOMY, 30)),
                    List.of("technocracy"))));

    static final ContentPack PACK =
            TestBackstory.pack(List.of(TECHNOCRACY), TestBackstory.FRAGMENTS, TestBackstory.COUNT, LEVELS);

    /** Лад з {@link #PACK}: перевага енергетики 120 → 100, війська −100, економіки 30, суспільства 0. */
    static final Regime REGIME = new Regime(
            TECHNOCRACY,
            TECHNOCRACY.subIdeologies().getFirst(),
            new TreeSet<>(List.of("authoritarian", "technocracy")),
            List.of());

    private TestDevelopment() {}

    static ModifierDef wheel(TechBranch branch, int value) {
        return new ModifierDef(ModifierTarget.wheel(DevelopmentWheel.kind(branch)), value);
    }

    private static DevelopmentLevelDef level(int level, List<String> tags) {
        int index = level + 3;
        return new DevelopmentLevelDef(
                level, "Рівень " + level, "Опис " + level, WEIGHTS[index], QUALITIES[index], tags);
    }
}
