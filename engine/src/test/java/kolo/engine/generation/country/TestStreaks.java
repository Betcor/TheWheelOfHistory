package kolo.engine.generation.country;

import java.util.List;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.StreakContent;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.content.StreakRewardId;
import kolo.engine.content.StreakWheelDef;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.Stat;

/**
 * Колеса стріків для тестових пакетів: «Золота доба» з трьома нагородами різної ваги (модифікатори на 10 років,
 * постійний модифікатор, додаткова постать) і «Андердог» з двома (жетони долі, мітка з модифікатором).
 */
public final class TestStreaks {

    /** Два модифікатори на 10 років; вага 100. */
    static final StreakRewardDef PRIDE = new StreakRewardDef(
            new StreakRewardId("national_pride"),
            "Національна гордість",
            "Опис",
            100,
            10,
            List.of(
                    new ModifierDef(ModifierTarget.stat(Stat.STABILITY), 10),
                    new ModifierDef(ModifierTarget.stat(Stat.LEGITIMACY), 5)),
            0,
            0,
            List.of());

    /** Постійний модифікатор; вага 300. */
    static final StreakRewardDef PRESTIGE = new StreakRewardDef(
            new StreakRewardId("world_prestige"),
            "Світовий престиж",
            "Опис",
            300,
            0,
            List.of(new ModifierDef(ModifierTarget.stat(Stat.INFLUENCE), 10)),
            0,
            0,
            List.of());

    /** Додаткова постать без модифікаторів; вага 600. */
    static final StreakRewardDef FIGURE = new StreakRewardDef(
            new StreakRewardId("great_figure"), "Видатна постать", "Опис", 600, 0, List.of(), 0, 1, List.of());

    /** Жетони долі; вага 100. */
    static final StreakRewardDef SECOND_CHANCE = new StreakRewardDef(
            new StreakRewardId("second_chance"), "Другий шанс", "Опис", 100, 0, List.of(), 2, 0, List.of());

    /** Мітка й модифікатор на 1 рік; вага 100. */
    static final StreakRewardDef SYMPATHY = new StreakRewardDef(
            new StreakRewardId("international_sympathy"),
            "Міжнародне співчуття",
            "Опис",
            100,
            1,
            List.of(new ModifierDef(ModifierTarget.stat(Stat.INFLUENCE), 5)),
            0,
            0,
            List.of("international_sympathy", "golden_age"));

    static final StreakWheelDef GOLDEN_AGE = new StreakWheelDef(
            StreakKind.GOLDEN_AGE,
            "Золота доба",
            "Опис",
            List.of("golden_age", "world_attention"),
            List.of(PRIDE, PRESTIGE, FIGURE));

    static final StreakWheelDef UNDERDOG = new StreakWheelDef(
            StreakKind.UNDERDOG, "Андердог", "Опис", List.of("underdog"), List.of(SECOND_CHANCE, SYMPATHY));

    public static final StreakContent CONTENT = new StreakContent(List.of(GOLDEN_AGE, UNDERDOG));

    private TestStreaks() {}
}
