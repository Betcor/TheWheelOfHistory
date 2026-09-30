package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.PopulationDef;
import kolo.engine.content.PopulationLevelDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.util.Fixed;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо населення держави (GD §4.1, колесо 4).
 *
 * <p>Сектори — рівні загального населення з контенту ({@code population.levels}) від найменшого до найбільшого
 * (рішення автора: загальне населення, а не щільність), з базовими вагами й рівнями результату з контенту. Перевага
 * складається з модифікаторів держави з ціллю {@link #KIND}, внеску площі — {@code (частка площі − 100) ×
 * population.area_advantage / 100} — і внеску географії — {@code (середня родючість держави − середня родючість
 * світу) × population.fertility_advantage / 100}: більша й родючіша держава частіше багатолюдна.
 *
 * <p>Населення ділиться між провінціями держави пропорційно вазі {@code province_base + родючість + coast_bonus} (бонус
 * — лише провінціям з виходом до моря), найбільшими залишками; при рівності — менша комірка.
 */
public final class PopulationWheel {

    public static final WheelKind KIND = new WheelKind("generation_population");

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private PopulationWheel() {}

    /**
     * @param rng окремий потік населення держави
     * @param modifiers модифікатори держави на момент колеса; діють ті, що мають ціль {@link #KIND}
     * @param area рівень площі держави з колеса площі
     * @param geography географія держави — провінції, берег і середня родючість
     * @param fertility родючість суходолу всієї карти — для середньої родючості світу
     * @throws ValidationException якщо рівня площі немає в контенті ({@link ErrorCode#UNKNOWN_REFERENCE}) або
     *     провінції держави не з цієї карти ({@link ErrorCode#VALUE_OUT_OF_RANGE})
     */
    public static StartPopulation generate(
            Rng rng,
            ContentPack content,
            List<Modifier> modifiers,
            AreaLevelId area,
            StartGeography geography,
            FertilityMap fertility) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(geography, "geography");
        Objects.requireNonNull(fertility, "fertility");
        AreaLevelDef areaLevel = content.map()
                .placement()
                .area(Objects.requireNonNull(area, "area"))
                .orElseThrow(() -> new ValidationException(
                        ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", "area", "value", area)));
        WheelSpin<PopulationLevelDef> spin = Wheel.spin(
                rng.fork("population"),
                KIND,
                sectors(content),
                advantage(content, modifiers, areaLevel, geography.fertility(), worldFertility(fertility)),
                content.balance().wheel().strength(KIND),
                TURN,
                null);
        PopulationLevelDef level = spin.value();
        return new StartPopulation(
                level.id(),
                level.populationK(),
                level.tier(),
                new TreeSet<>(level.tags()),
                level.quality(),
                provinces(content.map().population(), level.populationK(), geography, fertility),
                List.of(spin.record()));
    }

    /** Модифікатори з ціллю {@link #KIND}, потім ненульові внески площі ({@code area:<рівень>}) і родючості. */
    static Advantage advantage(
            ContentPack content,
            List<Modifier> modifiers,
            AreaLevelDef area,
            int countryFertility,
            int worldFertility) {
        List<AppliedModifier> contributions =
                new ArrayList<>(Modifiers.contributions(modifiers, ModifierTarget.wheel(KIND), TURN));
        PopulationDef def = content.map().population();
        int byArea = Fixed.mulDiv(area.sharePct() - 100, def.areaAdvantage(), 100);
        if (byArea != 0) {
            contributions.add(new AppliedModifier("area:" + area.id(), "area." + area.id(), byArea));
        }
        int byFertility = Fixed.mulDiv(countryFertility - worldFertility, def.fertilityAdvantage(), 100);
        if (byFertility != 0) {
            contributions.add(new AppliedModifier("geography:fertility", "geography.fertility", byFertility));
        }
        return Advantage.of(contributions);
    }

    /** Середня родючість суходолу карти, вниз. */
    static int worldFertility(FertilityMap fertility) {
        if (fertility.fertilities().isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "fertility"));
        }
        long sum = 0;
        for (int value : fertility.fertilities().values()) {
            sum += value;
        }
        return Math.toIntExact(sum / fertility.fertilities().size());
    }

    /** Населення провінцій: пропорційно вазі {@code province_base + родючість + coast_bonus}, найбільшими залишками. */
    static TreeMap<Integer, Integer> provinces(
            PopulationDef def, int populationK, StartGeography geography, FertilityMap fertility) {
        List<Integer> cells = geography.provinces();
        TreeSet<Integer> coastal = new TreeSet<>(geography.coastal());
        long[] weights = new long[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            int cell = cells.get(i);
            int value = fertility
                    .fertility(cell)
                    .orElseThrow(() -> new ValidationException(
                            ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "provinces", "value", cell)));
            weights[i] = def.provinceBase() + value + (coastal.contains(cell) ? def.coastBonus() : 0);
        }
        long[] shares = Fixed.distribute(populationK, weights);
        TreeMap<Integer, Integer> result = new TreeMap<>();
        for (int i = 0; i < cells.size(); i++) {
            result.put(cells.get(i), Math.toIntExact(shares[i]));
        }
        return result;
    }

    /** Сектор на кожен рівень у порядку контенту; id сектора — id рівня. */
    static List<Sector<PopulationLevelDef>> sectors(ContentPack content) {
        List<Sector<PopulationLevelDef>> sectors = new ArrayList<>();
        for (PopulationLevelDef level : content.map().population().levels()) {
            sectors.add(new Sector<>(
                    level.id().value(), level.weight(), level, level.quality(), level.tier(), level.tags()));
        }
        return sectors;
    }
}
