package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ContentPack;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо розміру армії (GD §4.1, колесо 10: залежить від ідеології, підкласифікації й ВВП; GD §4.4).
 *
 * <p>Сектори — рівні розміру армії з контенту від найменшої до найбільшої, з базовими вагами й рівнями результату з
 * контенту: додатна перевага зсуває шанси до більшої армії. Перевага складається з модифікаторів держави з ціллю
 * {@link #KIND} (ідеологія й підкласифікація) і внеску ВВП: крок рівня результату рівня ВВП від часткового × {@code
 * generation.army_size_gdp_advantage} з балансу.
 */
public final class ArmySizeWheel {

    public static final WheelKind KIND = new WheelKind("generation_army_size");

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private ArmySizeWheel() {}

    /**
     * @param rng окремий потік розміру армії держави
     * @param modifiers модифікатори держави на момент колеса (зараз — ладу, {@link Regime#modifiers()}); діють ті,
     *     що мають ціль {@link #KIND}
     * @param gdp стартовий ВВП держави
     */
    public static StartArmySize generate(Rng rng, ContentPack content, List<Modifier> modifiers, StartGdp gdp) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(gdp, "gdp");
        WheelSpin<ArmySizeDef> spin = Wheel.spin(
                rng.fork("army_size"),
                KIND,
                sectors(content),
                advantage(content, modifiers, gdp),
                content.balance().wheel().strength(KIND),
                TURN,
                null);
        ArmySizeDef size = spin.value();
        return new StartArmySize(
                size.id(), size.shareBp(), new TreeSet<>(size.tags()), size.quality(), List.of(spin.record()));
    }

    /** Модифікатори з ціллю {@link #KIND}, потім ненульовий внесок ВВП. */
    static Advantage advantage(ContentPack content, List<Modifier> modifiers, StartGdp gdp) {
        List<AppliedModifier> contributions =
                new ArrayList<>(Modifiers.contributions(modifiers, ModifierTarget.wheel(KIND), TURN));
        gdp.advantage(content.balance().generation().armySizeGdpAdvantage()).ifPresent(contributions::add);
        return Advantage.of(contributions);
    }

    /** Сектор на кожен рівень у порядку контенту; id сектора — id рівня. */
    static List<Sector<ArmySizeDef>> sectors(ContentPack content) {
        List<Sector<ArmySizeDef>> sectors = new ArrayList<>();
        for (ArmySizeDef size : content.armySizes()) {
            sectors.add(new Sector<>(size.id().value(), size.weight(), size, size.quality(), size.tier(), size.tags()));
        }
        return sectors;
    }
}
