package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.state.Development;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо технологічної розвиненості (GD §4.1, колесо 7; GD §4.3): окреме обертання для кожної галузі.
 *
 * <p>Сектори — рівні {@link Development#MIN}..{@link Development#MAX} з базовими вагами з контенту. Залежність від
 * ладу — через перевагу: модифікатори з ціллю {@link #kind(TechBranch)}; від населення — крок рівня результату
 * рівня населення × {@code generation.development_population_advantage} з балансу, однаково для всіх галузей. Рівні нижче світового — провали, вище —
 * успіхи, тож додатна перевага зсуває шанси до вищих рівнів, а крайні рівні ніколи не зникають (GD §2.3).
 */
public final class DevelopmentWheel {

    /** Префікс типу колеса; повний тип — з ключем галузі, напр. {@code generation_development_military}. */
    public static final String KIND_PREFIX = "generation_development_";

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private DevelopmentWheel() {}

    /** Тип колеса галузі: перевагу йому дають модифікатори з ціллю {@code wheel:generation_development_<галузь>}. */
    public static WheelKind kind(TechBranch branch) {
        return new WheelKind(KIND_PREFIX + branch.key());
    }

    /**
     * @param rng окремий потік розвиненості держави; розгалужується за ключем галузі, тож кидок однієї галузі не
     *     зсуває кидків інших
     * @param modifiers модифікатори держави на момент колеса (зараз — ладу, {@link Regime#modifiers()}); діють ті,
     *     що мають ціль {@link #kind(TechBranch)}
     */
    public static StartDevelopment generate(Rng rng, ContentPack content, List<Modifier> modifiers) {
        return generate(rng, content, modifiers, List.of());
    }

    /**
     * Те саме з внеском населення: кидки ті самі, змінюється лише перевага.
     *
     * @param population стартове населення держави
     */
    public static StartDevelopment generate(
            Rng rng, ContentPack content, List<Modifier> modifiers, StartPopulation population) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(population, "population");
        int perStep = content.balance().generation().developmentPopulationAdvantage();
        return generate(
                rng, content, modifiers, population.advantage(perStep).stream().toList());
    }

    /** @param extra внески в перевагу кожної галузі після модифікаторів */
    private static StartDevelopment generate(
            Rng rng, ContentPack content, List<Modifier> modifiers, List<AppliedModifier> extra) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(modifiers, "modifiers");
        List<Sector<DevelopmentLevelDef>> sectors = sectors(content);

        EnumMap<TechBranch, Integer> levels = new EnumMap<>(TechBranch.class);
        TreeSet<String> tags = new TreeSet<>();
        List<RollRecord> rolls = new ArrayList<>();
        int qualitySum = 0;
        for (TechBranch branch : TechBranch.values()) {
            WheelKind kind = kind(branch);
            WheelSpin<DevelopmentLevelDef> spin = Wheel.spin(
                    rng.fork(branch.key()),
                    kind,
                    sectors,
                    advantage(modifiers, kind, extra),
                    content.balance().wheel().strength(kind),
                    TURN,
                    null);
            levels.put(branch, spin.value().level());
            tags.addAll(spin.value().tags());
            rolls.add(spin.record());
            qualitySum += spin.outcome().quality();
        }
        int quality = Math.floorDiv(qualitySum, TechBranch.values().length);
        return new StartDevelopment(levels, tags, quality, rolls);
    }

    /** Модифікатори з ціллю колеса галузі, потім {@code extra}. */
    static Advantage advantage(List<Modifier> modifiers, WheelKind kind, List<AppliedModifier> extra) {
        List<AppliedModifier> contributions =
                new ArrayList<>(Modifiers.contributions(modifiers, ModifierTarget.wheel(kind), TURN));
        contributions.addAll(extra);
        return Advantage.of(contributions);
    }

    /** Сектор на кожен рівень за зростанням; однакові для всіх галузей. */
    static List<Sector<DevelopmentLevelDef>> sectors(ContentPack content) {
        List<Sector<DevelopmentLevelDef>> sectors = new ArrayList<>();
        for (DevelopmentLevelDef level : content.developmentLevels().values()) {
            sectors.add(new Sector<>(
                    sectorId(level.level()),
                    level.weight(),
                    level,
                    level.quality(),
                    tier(level.level()),
                    level.tags()));
        }
        return sectors;
    }

    /** Id сектора рівня: {@code level_minus_3}, {@code level_0}, {@code level_plus_2}. */
    static String sectorId(int level) {
        if (level < 0) {
            return "level_minus_" + -level;
        }
        return level == 0 ? "level_0" : "level_plus_" + level;
    }

    /** Як на рівень діє перевага: відставання — провал, випередження — успіх, крайні рівні — критичні. */
    static OutcomeTier tier(int level) {
        Development.check("level", level);
        if (level == Development.MIN) {
            return OutcomeTier.CRIT_FAIL;
        }
        if (level == Development.MAX) {
            return OutcomeTier.CRIT_SUCCESS;
        }
        if (level < Development.WORLD) {
            return OutcomeTier.FAIL;
        }
        return level == Development.WORLD ? OutcomeTier.PARTIAL : OutcomeTier.SUCCESS;
    }
}
