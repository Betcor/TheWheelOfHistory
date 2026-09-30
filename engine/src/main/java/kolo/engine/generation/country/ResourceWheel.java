package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.ResourceSuitabilityMap;
import kolo.engine.rng.Rng;
import kolo.engine.util.Fixed;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо ресурсів держави (GD §4.5): кілька родовищ у її провінціях.
 *
 * <ol>
 *   <li>Колесо {@link #COUNT_KIND} з рівними секторами «{@code resources_<n>}» обирає кількість обертань у межах
 *       рядка балансу за кількістю провінцій держави ({@code resources.count}).
 *   <li>Колесо {@link #RESOURCE_KIND} обирає ресурс серед ще не обраних (ресурси не повторюються — рішення автора),
 *       вага сектора — сума придатності провінцій держави до цього ресурсу: чим більше в державі гір, тим імовірніша
 *       руда. Ресурси без придатних провінцій на колесо не потрапляють.
 *   <li>Провінція родовища — вагою за придатністю серед провінцій держави, без колеса: гравець бачить обертання
 *       ресурсу, а місце — його наслідок. Провінція може мати кілька родовищ різних ресурсів.
 * </ol>
 *
 * <p>Колеса нейтральні: сектори {@link OutcomeTier#PARTIAL}, якість {@value #QUALITY}, перевага не діє. Якщо
 * придатних ресурсів забракло, родовищ менше за обрану кількість.
 */
public final class ResourceWheel {

    public static final WheelKind COUNT_KIND = new WheelKind("generation_resource_count");
    public static final WheelKind RESOURCE_KIND = new WheelKind("generation_resource");

    /** Кількості й ресурси нейтральні для стріків (GD §4.10): цінність ресурсу залежить від гри, а не від колеса. */
    static final int QUALITY = 50;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private ResourceWheel() {}

    /**
     * @param rng окремий потік ресурсів держави; розгалужується на кількість, ресурси й провінції
     * @param suitability придатність суходолу карти
     * @param provinces комірки суходолу держави; порядок і повтори не важливі
     * @throws ValidationException якщо провінцій немає ({@link ErrorCode#EMPTY_COLLECTION}) або серед них є вода чи
     *     комірка іншої карти ({@link ErrorCode#VALUE_OUT_OF_RANGE})
     */
    public static StartResources generate(
            Rng rng, ContentPack content, ResourceSuitabilityMap suitability, Collection<Integer> provinces) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(suitability, "suitability");
        SortedSet<Integer> cells = new TreeSet<>(provinces);
        if (cells.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "provinces"));
        }
        for (int cell : cells) {
            if (!suitability.isLand(cell)) {
                throw new ValidationException(
                        ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "provinces", "value", cell));
            }
        }

        Rng countRng = rng.fork("count");
        Rng resourceRng = rng.fork("resources");
        Rng provinceRng = rng.fork("provinces");

        List<RollRecord> rolls = new ArrayList<>();
        WheelSpin<Integer> countSpin = spin(content, countRng, COUNT_KIND, countSectors(content, cells.size()));
        rolls.add(countSpin.record());

        List<StartDeposit> deposits = new ArrayList<>();
        TreeSet<ResourceId> chosen = new TreeSet<>();
        for (int i = 0; i < countSpin.value(); i++) {
            List<Sector<ResourceId>> sectors = resourceSectors(content, suitability, cells, chosen);
            if (sectors.isEmpty()) {
                break;
            }
            WheelSpin<ResourceId> spin = spin(content, resourceRng, RESOURCE_KIND, sectors);
            rolls.add(spin.record());
            ResourceId resource = spin.value();
            chosen.add(resource);
            deposits.add(new StartDeposit(resource, province(provinceRng, suitability, cells, resource)));
        }
        return new StartResources(deposits, rolls);
    }

    /** Рівні сектори «{@code resources_<n>}» для кожної кількості з рядка балансу. */
    static List<Sector<Integer>> countSectors(ContentPack content, int provinces) {
        CountRange range = content.balance().resources().deposits(provinces);
        List<Sector<Integer>> sectors = new ArrayList<>();
        for (int count = range.min(); count <= range.max(); count++) {
            sectors.add(new Sector<>("resources_" + count, 1, count, QUALITY, OutcomeTier.PARTIAL, List.of()));
        }
        return sectors;
    }

    /**
     * Сектори ресурсів за зростанням id (як {@link ContentPack#resources()}). Сума придатності може перевищити найбільшу вагу сектора, тож ваги
     * переводяться в частки від {@link Wheel#TOTAL_BP} (вниз, щонайменше 1 — ресурс не зникає з колеса через
     * округлення); колесо потім нормалізує їх точно.
     */
    static List<Sector<ResourceId>> resourceSectors(
            ContentPack content,
            ResourceSuitabilityMap suitability,
            SortedSet<Integer> cells,
            SortedSet<ResourceId> chosen) {
        TreeMap<ResourceId, Long> sums = new TreeMap<>();
        long total = 0;
        for (ResourceDef resource : content.resources().values()) {
            if (chosen.contains(resource.id())) {
                continue;
            }
            long sum = 0;
            for (int cell : cells) {
                sum += suitability.suitability(cell, resource.id());
            }
            if (sum > 0) {
                sums.put(resource.id(), sum);
                total += sum;
            }
        }
        List<Sector<ResourceId>> sectors = new ArrayList<>();
        for (Map.Entry<ResourceId, Long> entry : sums.entrySet()) {
            int weight = (int) Math.max(1, Fixed.mulDiv(entry.getValue(), Wheel.TOTAL_BP, total));
            ResourceId id = entry.getKey();
            sectors.add(new Sector<>(id.value(), weight, id, QUALITY, OutcomeTier.PARTIAL, List.of()));
        }
        return sectors;
    }

    /** Провінція родовища: вагою за придатністю, комірки — за зростанням номера. */
    private static int province(
            Rng rng, ResourceSuitabilityMap suitability, SortedSet<Integer> cells, ResourceId resource) {
        TreeMap<Integer, Integer> weights = new TreeMap<>();
        int total = 0;
        for (int cell : cells) {
            int weight = suitability.suitability(cell, resource);
            if (weight > 0) {
                weights.put(cell, weight);
                total = Math.addExact(total, weight);
            }
        }
        int r = rng.nextInt(total);
        for (Map.Entry<Integer, Integer> entry : weights.entrySet()) {
            r -= entry.getValue();
            if (r < 0) {
                return entry.getKey();
            }
        }
        return weights.lastKey();
    }

    private static <T> WheelSpin<T> spin(ContentPack content, Rng rng, WheelKind kind, List<Sector<T>> sectors) {
        int strength = content.balance().wheel().strength(kind);
        return Wheel.spin(rng, kind, sectors, Advantage.NONE, strength, TURN, null);
    }
}
