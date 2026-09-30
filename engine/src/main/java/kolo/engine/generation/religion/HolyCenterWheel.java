package kolo.engine.generation.religion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.HolyCenterDef;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.PlacementMap;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.rng.Rng;
import kolo.engine.util.Fixed;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо святого центру релігії (GD §25.1): крутиться для кожної релігії світу після того, як усі держави обрали
 * свою релігію.
 *
 * <p>Сектори — провінції-кандидати в порядку комірок: провінції держав цієї віри й нічийні провінції. Якщо держав
 * цієї віри немає, лишаються нічийні; якщо й нічийних немає — увесь суходіл. Вага провінції — {@link
 * HolyCenterDef#weight}: родючість і річка роблять землю «обітованою», нічийна провінція важить менше. Ваги
 * переводяться в частки {@link Wheel#TOTAL_BP} вниз, щонайменше 1.
 *
 * <p>Релігії не заважають одна одній: одна провінція може бути святою для кількох вір. Вибір не буває кращим чи
 * гіршим: сектори мають рівень {@link OutcomeTier#PARTIAL}, нейтральну якість {@value #QUALITY}, перевага не діє.
 */
public final class HolyCenterWheel {

    public static final WheelKind KIND = new WheelKind("generation_holy_center");

    /** Святий центр нейтральний: генерація держав уже скінчилася, стріків немає. */
    static final int QUALITY = 50;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private HolyCenterWheel() {}

    /**
     * @param rng окремий потік святих центрів; для кожної релігії — {@code religion:<номер>}
     * @param religions скільки релігій у світі
     * @param countryReligions державна релігія кожної держави карти за номером держави; порожньо — світська
     * @throws ValidationException якщо держав не стільки, скільки на карті, або держава посилається на релігію поза
     *     світом ({@link ErrorCode#VALUE_OUT_OF_RANGE})
     */
    public static StartHolyCenters generate(
            Rng rng, ContentPack content, WorldMap map, int religions, List<OptionalInt> countryReligions) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(map, "map");
        Checks.inRange("religions", religions, 0, Integer.MAX_VALUE);
        if (countryReligions.size() != map.countries()) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE,
                    ErrorDetails.of("field", "country_religions", "value", countryReligions.size()));
        }
        List<TreeSet<Integer>> followers = new ArrayList<>();
        for (int r = 0; r < religions; r++) {
            followers.add(new TreeSet<>());
        }
        for (int n = 0; n < countryReligions.size(); n++) {
            OptionalInt religion = countryReligions.get(n);
            if (religion.isPresent()) {
                followers
                        .get(Checks.inRange("country_religions", religion.getAsInt(), 0, religions - 1))
                        .add(n);
            }
        }

        HolyCenterDef def = content.balance().religion().holyCenter();
        SortedSet<Integer> land = new TreeSet<>(map.fertility().fertilities().keySet());
        int strength = content.balance().wheel().strength(KIND);
        List<HolyCenter> centers = new ArrayList<>();
        for (int r = 0; r < religions; r++) {
            List<Integer> candidates = candidates(map.placement().cellCountries(), land, followers.get(r));
            WheelSpin<Integer> spin = Wheel.spin(
                    rng.fork("religion:" + r),
                    KIND,
                    sectors(def, map, candidates),
                    Advantage.NONE,
                    strength,
                    TURN,
                    null);
            centers.add(new HolyCenter(spin.value(), spin.record()));
        }
        return new StartHolyCenters(centers);
    }

    /**
     * Провінції-кандидати за зростанням комірки: держав з {@code followers} і нічийні; якщо таких немає — увесь
     * суходіл.
     *
     * @param cellCountries держава кожної комірки або {@link PlacementMap#NONE}
     * @param land комірки суходолу за зростанням
     * @param followers номери держав цієї віри
     */
    static List<Integer> candidates(
            List<Integer> cellCountries, SortedSet<Integer> land, SortedSet<Integer> followers) {
        List<Integer> candidates = new ArrayList<>();
        for (int cell : land) {
            int owner = cellCountries.get(cell);
            if (owner == PlacementMap.NONE || followers.contains(owner)) {
                candidates.add(cell);
            }
        }
        return candidates.isEmpty() ? List.copyOf(land) : List.copyOf(candidates);
    }

    /** Сектори {@code province_<комірка>} у порядку кандидатів; вага — частка {@link Wheel#TOTAL_BP}, мінімум 1. */
    static List<Sector<Integer>> sectors(HolyCenterDef def, WorldMap map, List<Integer> candidates) {
        long[] weights = new long[candidates.size()];
        long total = 0;
        for (int i = 0; i < weights.length; i++) {
            int cell = candidates.get(i);
            int fertility = map.fertility().fertility(cell).orElseThrow();
            weights[i] = def.weight(
                    !map.placement().isClaimed(cell), fertility, map.rivers().hasRiver(cell));
            total += weights[i];
        }
        List<Sector<Integer>> sectors = new ArrayList<>(weights.length);
        for (int i = 0; i < weights.length; i++) {
            int weight = (int) Math.max(1, Fixed.mulDiv(weights[i], Wheel.TOTAL_BP, total));
            int cell = candidates.get(i);
            sectors.add(new Sector<>("province_" + cell, weight, cell, QUALITY, OutcomeTier.PARTIAL, List.of()));
        }
        return sectors;
    }
}
