package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.GenerationBalanceDef;
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо вишколу армії (GD §4.1, колесо 12: залежить від ВВП, ідеології й розвиненості; GD §4.4).
 *
 * <p>Сектори — рівні вишколу з контенту від ополчення до еліти, з базовими вагами й рівнями результату з контенту:
 * додатна перевага зсуває шанси до кращого вишколу. Перевага складається з модифікаторів держави з ціллю {@link
 * #KIND} (ідеологія й підкласифікація), внеску ВВП — крок рівня результату рівня ВВП від часткового × {@code
 * generation.army_training_gdp_advantage} — і внеску військової галузі: її рівень розвиненості × {@code
 * generation.army_training_development_advantage}.
 */
public final class ArmyTrainingWheel {

    public static final WheelKind KIND = new WheelKind("generation_army_training");

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private ArmyTrainingWheel() {}

    /**
     * @param rng окремий потік вишколу армії держави
     * @param modifiers модифікатори держави на момент колеса (зараз — ладу, {@link Regime#modifiers()}); діють ті,
     *     що мають ціль {@link #KIND}
     * @param gdp стартовий ВВП держави
     * @param development стартова розвиненість держави; діє військова галузь
     */
    public static StartArmyTraining generate(
            Rng rng, ContentPack content, List<Modifier> modifiers, StartGdp gdp, StartDevelopment development) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(gdp, "gdp");
        Objects.requireNonNull(development, "development");
        WheelSpin<TrainingLevelDef> spin = Wheel.spin(
                rng.fork("army_training"),
                KIND,
                sectors(content),
                advantage(content, modifiers, gdp, development),
                content.balance().wheel().strength(KIND),
                TURN,
                null);
        TrainingLevelDef level = spin.value();
        return new StartArmyTraining(
                level.level(),
                level.combatModifier(),
                new TreeSet<>(level.tags()),
                level.quality(),
                List.of(spin.record()));
    }

    /** Модифікатори з ціллю {@link #KIND}, потім ненульові внески ВВП і військової галузі. */
    static Advantage advantage(
            ContentPack content, List<Modifier> modifiers, StartGdp gdp, StartDevelopment development) {
        GenerationBalanceDef balance = content.balance().generation();
        List<AppliedModifier> contributions =
                new ArrayList<>(Modifiers.contributions(modifiers, ModifierTarget.wheel(KIND), TURN));
        gdp.advantage(balance.armyTrainingGdpAdvantage()).ifPresent(contributions::add);
        development
                .advantage(TechBranch.MILITARY, balance.armyTrainingDevelopmentAdvantage())
                .ifPresent(contributions::add);
        return Advantage.of(contributions);
    }

    /** Сектор на кожен рівень від ополчення до еліти; id сектора — {@link #sectorId}. */
    static List<Sector<TrainingLevelDef>> sectors(ContentPack content) {
        List<Sector<TrainingLevelDef>> sectors = new ArrayList<>();
        for (TrainingLevelDef level : content.trainingLevels().values()) {
            sectors.add(new Sector<>(
                    sectorId(level.level()), level.weight(), level, level.quality(), level.tier(), level.tags()));
        }
        return sectors;
    }

    /** Id сектора рівня: {@code level_1} … {@code level_5}. */
    static String sectorId(int level) {
        return "level_" + level;
    }
}
