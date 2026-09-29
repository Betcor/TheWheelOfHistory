package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.HdiLevelDef;
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
 * Колесо індексу людського розвитку (GD §4.1, колесо 9: залежить від ВВП й ідеології).
 *
 * <p>Сектори — рівні ІЛР з контенту від найнижчого до найвищого, з базовими вагами й рівнями результату з контенту:
 * додатна перевага зсуває шанси до вищого розвитку. Перевага складається з модифікаторів держави з ціллю {@link
 * #KIND} (лад) і внеску ВВП: крок рівня результату рівня ВВП від часткового × {@code generation.hdi_gdp_advantage}
 * з балансу.
 */
public final class HdiWheel {

    public static final WheelKind KIND = new WheelKind("generation_hdi");

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private HdiWheel() {}

    /**
     * @param rng окремий потік ІЛР держави
     * @param modifiers модифікатори держави на момент колеса (зараз — ладу, {@link Regime#modifiers()}); діють ті,
     *     що мають ціль {@link #KIND}
     * @param gdp стартовий ВВП держави
     */
    public static StartHdi generate(Rng rng, ContentPack content, List<Modifier> modifiers, StartGdp gdp) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(gdp, "gdp");
        WheelSpin<HdiLevelDef> spin = Wheel.spin(
                rng.fork("hdi"),
                KIND,
                sectors(content),
                advantage(content, modifiers, gdp),
                content.balance().wheel().strength(KIND),
                TURN,
                null);
        HdiLevelDef level = spin.value();
        return new StartHdi(
                level.id(), level.hdi(), new TreeSet<>(level.tags()), level.quality(), List.of(spin.record()));
    }

    /** Модифікатори з ціллю {@link #KIND}, потім ненульовий внесок ВВП. */
    static Advantage advantage(ContentPack content, List<Modifier> modifiers, StartGdp gdp) {
        List<AppliedModifier> contributions =
                new ArrayList<>(Modifiers.contributions(modifiers, ModifierTarget.wheel(KIND), TURN));
        gdp.advantage(content.balance().generation().hdiGdpAdvantage()).ifPresent(contributions::add);
        return Advantage.of(contributions);
    }

    /** Сектор на кожен рівень у порядку контенту; id сектора — id рівня. */
    static List<Sector<HdiLevelDef>> sectors(ContentPack content) {
        List<Sector<HdiLevelDef>> sectors = new ArrayList<>();
        for (HdiLevelDef level : content.hdiLevels()) {
            sectors.add(new Sector<>(
                    level.id().value(), level.weight(), level, level.quality(), level.tier(), level.tags()));
        }
        return sectors;
    }
}
