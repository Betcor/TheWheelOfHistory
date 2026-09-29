package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.state.NuclearStatus;
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
 * Колесо ядерного статусу (GD §4.1, колесо 14; GD §4.6), а для арсеналу — ще й колесо кількості боєголовок.
 *
 * <ol>
 *   <li>Колесо {@link #KIND}: сектори — статуси з базовими вагами з контенту. Відсутність зброї — провал, програма —
 *       успіх, арсенал — критичний успіх, тож додатна перевага веде до ядерної зброї. Перевага складається з
 *       модифікаторів держави з ціллю {@link #KIND} (лад) і внеску розвиненості енергетики й науки: рівень ×
 *       {@code generation.nuclear_energy_advantage} з балансу.
 *   <li>Арсенал неможливий (сектора немає), якщо в держави немає ресурсу з міткою {@value #NUCLEAR_FUEL_TAG} або
 *       енергетика й наука нижче за {@value #ARSENAL_MIN_ENERGY_LEVEL} (GD §4.6). Програма можлива завжди.
 *   <li>Колесо {@link #WARHEADS_KIND} крутиться лише для арсеналу: рівні сектори «{@code warheads_<n>}» у межах
 *       балансу ({@code generation.warheads}); перевага на нього не діє.
 * </ol>
 */
public final class NuclearWheel {

    public static final WheelKind KIND = new WheelKind("generation_nuclear");
    public static final WheelKind WARHEADS_KIND = new WheelKind("generation_warheads");

    /** Мітка ресурсу, без якого арсенал неможливий (у вбудованому контенті — уран). */
    public static final String NUCLEAR_FUEL_TAG = "nuclear_fuel";

    /** Найнижчий рівень енергетики й науки, з яким можливий арсенал: з відставанням −2 і нижче — ні. */
    public static final int ARSENAL_MIN_ENERGY_LEVEL = -1;

    /** Кількість боєголовок нейтральна для держави: якість арсеналу вже врахована статусом. */
    static final int WARHEADS_QUALITY = 50;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private NuclearWheel() {}

    /**
     * @param rng окремий потік ядерного статусу держави; розгалужується на статус і боєголовки
     * @param modifiers модифікатори держави на момент колеса (зараз — ладу, {@link Regime#modifiers()}); діють ті,
     *     що мають ціль {@link #KIND}
     * @param development стартова розвиненість держави
     * @param resources ресурси держави; кожен має бути в контенті, порядок і повтори не важливі
     */
    public static StartNuclear generate(
            Rng rng,
            ContentPack content,
            List<Modifier> modifiers,
            StartDevelopment development,
            Collection<ResourceId> resources) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(development, "development");
        boolean arsenalAllowed = arsenalAllowed(content, development, resources);

        List<RollRecord> rolls = new ArrayList<>();
        WheelSpin<NuclearStatusDef> spin = Wheel.spin(
                rng.fork("status"),
                KIND,
                sectors(content, arsenalAllowed),
                advantage(content, modifiers, development),
                content.balance().wheel().strength(KIND),
                TURN,
                null);
        rolls.add(spin.record());
        NuclearStatusDef status = spin.value();

        int warheads = 0;
        if (status.status() == NuclearStatus.ARSENAL) {
            WheelSpin<Integer> count = Wheel.spin(
                    rng.fork("warheads"),
                    WARHEADS_KIND,
                    warheadSectors(content),
                    Advantage.NONE,
                    content.balance().wheel().strength(WARHEADS_KIND),
                    TURN,
                    null);
            rolls.add(count.record());
            warheads = count.value();
        }
        return new StartNuclear(status.status(), warheads, new TreeSet<>(status.tags()), status.quality(), rolls);
    }

    /** Чи може держава з такою розвиненістю й ресурсами мати арсенал на старті (GD §4.6). */
    public static boolean arsenalAllowed(
            ContentPack content, StartDevelopment development, Collection<ResourceId> resources) {
        boolean fuel = false;
        for (ResourceId id : resources) {
            ResourceDef resource = content.resource(Objects.requireNonNull(id, "resource"))
                    .orElseThrow(() -> new ValidationException(
                            ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", "resources", "value", id.value())));
            fuel |= resource.tags().contains(NUCLEAR_FUEL_TAG);
        }
        return fuel && development.level(TechBranch.ENERGY_SCIENCE) >= ARSENAL_MIN_ENERGY_LEVEL;
    }

    /** Модифікатори з ціллю {@link #KIND}, потім внесок енергетики й науки, якщо він не нульовий. */
    static Advantage advantage(ContentPack content, List<Modifier> modifiers, StartDevelopment development) {
        List<AppliedModifier> contributions =
                new ArrayList<>(Modifiers.contributions(modifiers, ModifierTarget.wheel(KIND), TURN));
        development
                .advantage(
                        TechBranch.ENERGY_SCIENCE,
                        content.balance().generation().nuclearEnergyAdvantage())
                .ifPresent(contributions::add);
        return Advantage.of(contributions);
    }

    /** Сектор на кожен статус у порядку {@link NuclearStatus}; без арсеналу, якщо він неможливий. */
    static List<Sector<NuclearStatusDef>> sectors(ContentPack content, boolean arsenalAllowed) {
        List<Sector<NuclearStatusDef>> sectors = new ArrayList<>();
        for (NuclearStatusDef status : content.nuclearStatuses().values()) {
            if (status.status() == NuclearStatus.ARSENAL && !arsenalAllowed) {
                continue;
            }
            sectors.add(new Sector<>(
                    status.status().key(),
                    status.weight(),
                    status,
                    status.quality(),
                    tier(status.status()),
                    status.tags()));
        }
        return sectors;
    }

    /** Як на статус діє перевага: без зброї — провал, програма — успіх, арсенал — критичний успіх. */
    static OutcomeTier tier(NuclearStatus status) {
        return switch (status) {
            case NONE -> OutcomeTier.FAIL;
            case PROGRAM -> OutcomeTier.SUCCESS;
            case ARSENAL -> OutcomeTier.CRIT_SUCCESS;
        };
    }

    /** Рівні сектори «{@code warheads_<n>}» для кожної кількості з балансу. */
    static List<Sector<Integer>> warheadSectors(ContentPack content) {
        CountRange range = content.balance().generation().warheads();
        List<Sector<Integer>> sectors = new ArrayList<>();
        for (int count = range.min(); count <= range.max(); count++) {
            sectors.add(new Sector<>("warheads_" + count, 1, count, WARHEADS_QUALITY, OutcomeTier.PARTIAL, List.of()));
        }
        return sectors;
    }
}
