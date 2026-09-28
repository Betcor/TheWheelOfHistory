package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колеса ладу держави (GD §4.1, колеса 5–6): спершу ідеологія, потім підкласифікація серед підкласифікацій обраної
 * ідеології.
 *
 * <p>Ідеологія ні від чого не залежить, тож ваги секторів — лише з контенту ({@code weight}), у порядку контенту.
 * Лад не буває добрим чи поганим для держави: якість секторів нейтральна й не впливає на стріки. Перевага не діє —
 * сектори мають рівень {@link OutcomeTier#PARTIAL}.
 */
public final class RegimeWheel {

    public static final WheelKind IDEOLOGY_KIND = new WheelKind("generation_ideology");
    public static final WheelKind SUB_IDEOLOGY_KIND = new WheelKind("generation_sub_ideology");

    /** Лад нейтральний для держави: не рахується ні в добрі, ні в погані стріки. */
    static final int QUALITY = 50;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private RegimeWheel() {}

    /**
     * @param rng окремий потік ладу держави; всередині розгалужується на ідеологію й підкласифікацію, тож зміна
     *     одного колеса не зсуває кидків іншого
     */
    public static Regime generate(Rng rng, ContentPack content) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Rng ideologyRng = rng.fork("ideology");
        Rng subIdeologyRng = rng.fork("sub_ideology");

        WheelSpin<IdeologyDef> ideology = spin(content, ideologyRng, IDEOLOGY_KIND, ideologySectors(content));
        WheelSpin<SubIdeologyDef> sub =
                spin(content, subIdeologyRng, SUB_IDEOLOGY_KIND, subIdeologySectors(ideology.value()));

        TreeSet<String> tags = new TreeSet<>(ideology.value().tags());
        tags.addAll(sub.value().tags());
        List<RollRecord> rolls = List.of(ideology.record(), sub.record());
        return new Regime(ideology.value(), sub.value(), tags, rolls);
    }

    /** Сектор на кожну ідеологію в порядку контенту. */
    static List<Sector<IdeologyDef>> ideologySectors(ContentPack content) {
        List<Sector<IdeologyDef>> sectors = new ArrayList<>();
        for (IdeologyDef ideology : content.ideologiesInContentOrder()) {
            sectors.add(new Sector<>(
                    ideology.id().value(), ideology.weight(), ideology, QUALITY, OutcomeTier.PARTIAL, ideology.tags()));
        }
        return sectors;
    }

    /** Сектор на кожну підкласифікацію ідеології в порядку контенту. */
    static List<Sector<SubIdeologyDef>> subIdeologySectors(IdeologyDef ideology) {
        List<Sector<SubIdeologyDef>> sectors = new ArrayList<>();
        for (SubIdeologyDef sub : ideology.subIdeologies()) {
            sectors.add(new Sector<>(sub.id().value(), sub.weight(), sub, QUALITY, OutcomeTier.PARTIAL, sub.tags()));
        }
        return sectors;
    }

    private static <T> WheelSpin<T> spin(ContentPack content, Rng rng, WheelKind kind, List<Sector<T>> sectors) {
        int strength = content.balance().wheel().strength(kind);
        return Wheel.spin(rng, kind, sectors, Advantage.NONE, strength, TURN, null);
    }
}
