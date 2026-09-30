package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import kolo.engine.content.CoastLevelDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
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
 * Колесо ВВП на душу (GD §4.1, колесо 8).
 *
 * <p>Сектори — рівні ВВП з контенту від найбіднішого до найбагатшого, з базовими вагами й рівнями результату з
 * контенту: додатна перевага зсуває шанси до багатших рівнів. Перевага складається з модифікаторів держави з ціллю
 * {@link #KIND} (лад) і внесків розвиненості {@link #DEVELOPMENT_BRANCHES}: рівень кожної з галузей × {@code
 * generation.gdp_development_advantage} з балансу; із картою — ще внесок населення (крок рівня результату рівня
 * населення × {@code generation.gdp_population_advantage}) і берега ({@link CoastLevelDef#gdpAdvantage()} рівня виходу
 * до моря держави).
 */
public final class GdpWheel {

    public static final WheelKind KIND = new WheelKind("generation_gdp");

    /** Галузі, розвиненість яких зсуває ВВП (GD §4.1: «залежить від розвиненості»): господарство й суспільство. */
    public static final List<TechBranch> DEVELOPMENT_BRANCHES = List.of(TechBranch.ECONOMY, TechBranch.SOCIETY);

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private GdpWheel() {}

    /**
     * @param rng окремий потік ВВП держави
     * @param modifiers модифікатори держави на момент колеса (зараз — ладу, {@link Regime#modifiers()}); діють ті,
     *     що мають ціль {@link #KIND}
     * @param development стартова розвиненість держави
     */
    public static StartGdp generate(
            Rng rng, ContentPack content, List<Modifier> modifiers, StartDevelopment development) {
        return generate(rng, content, modifiers, development, List.of());
    }

    /**
     * Те саме з внесками населення й берега: кидок той самий, змінюється лише перевага.
     *
     * @param population стартове населення держави
     * @param geography географія держави — рівень виходу до моря
     * @throws ValidationException якщо рівня виходу до моря немає в контенті ({@link ErrorCode#UNKNOWN_REFERENCE})
     */
    public static StartGdp generate(
            Rng rng,
            ContentPack content,
            List<Modifier> modifiers,
            StartDevelopment development,
            StartPopulation population,
            StartGeography geography) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(population, "population");
        Objects.requireNonNull(geography, "geography");
        List<AppliedModifier> extra = new ArrayList<>();
        population
                .advantage(content.balance().generation().gdpPopulationAdvantage())
                .ifPresent(extra::add);
        coast(content, geography).ifPresent(extra::add);
        return generate(rng, content, modifiers, development, extra);
    }

    private static StartGdp generate(
            Rng rng,
            ContentPack content,
            List<Modifier> modifiers,
            StartDevelopment development,
            List<AppliedModifier> extra) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(development, "development");
        Advantage advantage = advantage(content, modifiers, development, extra);
        WheelSpin<GdpLevelDef> spin = Wheel.spin(
                rng.fork("per_capita"),
                KIND,
                sectors(content),
                advantage,
                content.balance().wheel().strength(KIND),
                TURN,
                null);
        GdpLevelDef level = spin.value();
        return new StartGdp(
                level.id(),
                level.perCapita(),
                level.tier(),
                new TreeSet<>(level.tags()),
                level.quality(),
                List.of(spin.record()));
    }

    /**
     * Модифікатори з ціллю {@link #KIND}, потім ненульові внески галузей у порядку {@link #DEVELOPMENT_BRANCHES},
     * потім {@code extra} (населення, берег).
     */
    static Advantage advantage(
            ContentPack content, List<Modifier> modifiers, StartDevelopment development, List<AppliedModifier> extra) {
        List<AppliedModifier> contributions =
                new ArrayList<>(Modifiers.contributions(modifiers, ModifierTarget.wheel(KIND), TURN));
        int perLevel = content.balance().generation().gdpDevelopmentAdvantage();
        for (TechBranch branch : DEVELOPMENT_BRANCHES) {
            development.advantage(branch, perLevel).ifPresent(contributions::add);
        }
        contributions.addAll(extra);
        return Advantage.of(contributions);
    }

    /** Внесок рівня виходу до моря: id {@code coast:<рівень>}, ключ {@code coast.<рівень>}; нуль — без рядка. */
    static Optional<AppliedModifier> coast(ContentPack content, StartGeography geography) {
        CoastLevelDef level = content.map().geography().coast().stream()
                .filter(def -> def.id().equals(geography.coast()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        ErrorCode.UNKNOWN_REFERENCE,
                        ErrorDetails.of(
                                "field", "coast", "value", geography.coast().value())));
        if (level.gdpAdvantage() == 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedModifier("coast:" + level.id(), "coast." + level.id(), level.gdpAdvantage()));
    }

    /** Сектор на кожен рівень у порядку контенту; id сектора — id рівня. */
    static List<Sector<GdpLevelDef>> sectors(ContentPack content) {
        List<Sector<GdpLevelDef>> sectors = new ArrayList<>();
        for (GdpLevelDef level : content.gdpLevels()) {
            sectors.add(new Sector<>(
                    level.id().value(), level.weight(), level, level.quality(), level.tier(), level.tags()));
        }
        return sectors;
    }
}
