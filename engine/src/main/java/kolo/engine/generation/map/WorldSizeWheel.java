package kolo.engine.generation.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.MapTemplateId;
import kolo.engine.content.WorldBalanceDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.WorldLimits;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Розмір світу (GD §3.2–3.3) — перше, що генерується: від нього залежать карта, релігії й держави.
 *
 * <p>Колеса в порядку кидків:
 *
 * <ol>
 *   <li>{@link #NPC_KIND} — скільки NPC додається до кількості гравців: рівні сектори {@code npc_<n>} у діапазоні
 *       частки NPC; держав разом — не більше {@link WorldLimits#MAX_COUNTRIES};
 *   <li>{@link #TEMPLATE_KIND} — шаблон карти за вагами контенту, серед шаблонів, чий діапазон материків вміщує
 *       зафіксовану хостом кількість;
 *   <li>{@link #CONTINENTS_KIND} — рівні сектори {@code continents_<n>} у діапазоні шаблону;
 *   <li>{@link #PROVINCES_KIND} — провінцій на державу: рівні сектори {@code provinces_<n>} з кроком балансу;
 *   <li>{@link #UNCLAIMED_KIND} — частка нічийних земель: рівні сектори {@code unclaimed_<bp>} з кроком балансу.
 * </ol>
 *
 * <p>Шаблон і материки, зафіксовані хостом, не крутяться. Провінцій = держав × провінцій на державу × коефіцієнт
 * шаблону / 100 (вниз), у межах балансу. Розмір світу не добрий і не поганий: сектори {@link OutcomeTier#PARTIAL},
 * без переваги.
 */
public final class WorldSizeWheel {

    public static final WheelKind NPC_KIND = new WheelKind("world_npc_count");
    public static final WheelKind TEMPLATE_KIND = new WheelKind("world_map_template");
    public static final WheelKind CONTINENTS_KIND = new WheelKind("world_continents");
    public static final WheelKind PROVINCES_KIND = new WheelKind("world_provinces_per_country");
    public static final WheelKind UNCLAIMED_KIND = new WheelKind("world_unclaimed_land");

    /** Розмір світу нейтральний: стріків на рівні світу немає, але якість сектора обов'язкова. */
    static final int QUALITY = 50;

    /** Світ генерується до першого ходу. */
    private static final int TURN = 0;

    private WorldSizeWheel() {}

    /**
     * @param rng окремий потік розміру світу; всередині розгалужується на кожне колесо ({@code npc},
     *     {@code template}, {@code continents}, {@code provinces}, {@code unclaimed}), тож зафіксований хостом
     *     параметр не зсуває кидків інших коліс
     * @throws ValidationException якщо зафіксованого шаблону немає в контенті ({@link ErrorCode#UNKNOWN_REFERENCE})
     *     або зафіксовану кількість материків не дає жоден придатний шаблон ({@link ErrorCode#VALUE_OUT_OF_RANGE})
     */
    public static WorldSize generate(Rng rng, ContentPack content, WorldSizeInput input) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(input, "input");
        WorldBalanceDef balance = content.balance().world();
        List<RollRecord> rolls = new ArrayList<>();

        WheelSpin<Integer> npcSpin =
                spin(content, rng.fork("npc"), NPC_KIND, rangeSectors("npc_", balance.npcExtra(input.npcShare())));
        rolls.add(npcSpin.record());
        int npc = Math.min(input.players() + npcSpin.value(), WorldLimits.MAX_COUNTRIES - input.players());

        List<MapTemplateDef> candidates = templateCandidates(content, input);
        MapTemplateDef template;
        if (input.template().isPresent()) {
            template = candidates.getFirst();
        } else {
            WheelSpin<MapTemplateDef> templateSpin =
                    spin(content, rng.fork("template"), TEMPLATE_KIND, templateSectors(candidates));
            rolls.add(templateSpin.record());
            template = templateSpin.value();
        }

        int continents;
        if (input.continents().isPresent()) {
            continents = input.continents().getAsInt();
        } else {
            WheelSpin<Integer> continentSpin = spin(
                    content,
                    rng.fork("continents"),
                    CONTINENTS_KIND,
                    rangeSectors("continents_", template.continents()));
            rolls.add(continentSpin.record());
            continents = continentSpin.value();
        }

        WheelSpin<Integer> provincesSpin = spin(
                content,
                rng.fork("provinces"),
                PROVINCES_KIND,
                valueSectors("provinces_", balance.provincesPerCountry().values()));
        rolls.add(provincesSpin.record());
        int countries = input.players() + npc;
        long raw = (long) countries * provincesSpin.value() * template.provincesPct() / 100;
        CountRange limits = balance.provinces();
        int provinces = Math.clamp(raw, limits.min(), limits.max());

        WheelSpin<Integer> unclaimedSpin = spin(
                content,
                rng.fork("unclaimed"),
                UNCLAIMED_KIND,
                valueSectors("unclaimed_", balance.unclaimedBp().values()));
        rolls.add(unclaimedSpin.record());

        return new WorldSize(
                input.players(),
                npc,
                template.id(),
                continents,
                provincesSpin.value(),
                provinces,
                unclaimedSpin.value(),
                rolls);
    }

    /** Шаблони, з яких можна обирати: зафіксований хостом або всі в порядку контенту — з потрібною кількістю материків. */
    static List<MapTemplateDef> templateCandidates(ContentPack content, WorldSizeInput input) {
        List<MapTemplateDef> templates;
        if (input.template().isPresent()) {
            MapTemplateId id = input.template().get();
            MapTemplateDef fixed = content.map()
                    .template(id)
                    .orElseThrow(() -> new ValidationException(
                            ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", "template", "value", id)));
            templates = List.of(fixed);
        } else {
            templates = content.map().templates();
        }
        if (input.continents().isEmpty()) {
            return templates;
        }
        int continents = input.continents().getAsInt();
        List<MapTemplateDef> fitting = templates.stream()
                .filter(template -> template.continents().contains(continents))
                .toList();
        if (fitting.isEmpty()) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE,
                    input.template().isPresent()
                            ? ErrorDetails.of(
                                    "field",
                                    "continents",
                                    "value",
                                    continents,
                                    "min",
                                    templates.getFirst().continents().min(),
                                    "max",
                                    templates.getFirst().continents().max())
                            : ErrorDetails.of("field", "continents", "value", continents));
        }
        return fitting;
    }

    static List<Sector<MapTemplateDef>> templateSectors(List<MapTemplateDef> templates) {
        List<Sector<MapTemplateDef>> sectors = new ArrayList<>();
        for (MapTemplateDef template : templates) {
            sectors.add(new Sector<>(
                    template.id().value(), template.weight(), template, QUALITY, OutcomeTier.PARTIAL, List.of()));
        }
        return sectors;
    }

    /** Рівний сектор на кожне число діапазону. */
    static List<Sector<Integer>> rangeSectors(String prefix, CountRange range) {
        List<Integer> values = new ArrayList<>();
        for (int value = range.min(); value <= range.max(); value++) {
            values.add(value);
        }
        return valueSectors(prefix, values);
    }

    /** Рівний сектор на кожне значення, {@code <prefix><значення>}. */
    static List<Sector<Integer>> valueSectors(String prefix, List<Integer> values) {
        IntFunction<Sector<Integer>> sector =
                value -> new Sector<>(prefix + value, 1, value, QUALITY, OutcomeTier.PARTIAL, List.of());
        return values.stream().map(sector::apply).toList();
    }

    private static <T> WheelSpin<T> spin(ContentPack content, Rng rng, WheelKind kind, List<Sector<T>> sectors) {
        int strength = content.balance().wheel().strength(kind);
        return Wheel.spin(rng, kind, sectors, Advantage.NONE, strength, TURN, null);
    }
}
