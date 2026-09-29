package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Climate;
import kolo.engine.state.Cover;
import kolo.engine.state.Relief;

/**
 * Числа генерації клімату (GD §3.5): клімати світу, температура й волога провінцій, пороги поясів і умови покриву.
 *
 * <p>Пояс за температурою {@code t} і вологою {@code m}: {@code t < polarBelow} — полярний; {@code t < borealBelow} —
 * бореальний; {@code m < aridBelow} — посушливий; {@code t ≥ tropicalFrom} — тропічний; інакше — помірний. Холод
 * важить більше за посуху: сухий полярний край — полярний.
 *
 * @param worlds клімати світу в порядку контенту (порядок секторів колеса); непорожні, id без повторів
 * @param temperature числа температури
 * @param moisture числа вологи
 * @param noiseCells розмір плям шуму температури й вологи в комірках, {@code 1..}{@value ContinentsDef#MAX_NOISE_CELLS}
 * @param polarBelow температура, нижче якої пояс полярний, {@code 0..}{@value #MAX_VALUE}
 * @param borealBelow температура, нижче якої пояс бореальний; строго вище {@code polarBelow}
 * @param tropicalFrom температура, з якої пояс тропічний; строго вище {@code borealBelow}, не вище {@value #MAX_VALUE}
 * @param aridBelow волога, нижче якої теплий пояс посушливий, {@code 0..}{@value #MAX_VALUE}
 * @param zones пояси в порядку {@link Climate}, кожен рівно раз
 * @param covers покриви в порядку перевірки умов, кожен рівно раз
 */
public record ClimateDef(
        List<WorldClimateDef> worlds,
        ClimateTemperatureDef temperature,
        ClimateMoistureDef moisture,
        int noiseCells,
        int polarBelow,
        int borealBelow,
        int tropicalFrom,
        int aridBelow,
        List<ClimateZoneDef> zones,
        List<CoverDef> covers) {

    /** Температура й волога — {@code 0..MAX_VALUE}, як висота й показники держави. */
    public static final int MAX_VALUE = 100;

    public ClimateDef {
        worlds = List.copyOf(Objects.requireNonNull(worlds, "climate.worlds"));
        if (worlds.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "climate.worlds"));
        }
        Defs.uniqueAll(
                "world_climate.id", worlds.stream().map(WorldClimateDef::id).toList());
        Objects.requireNonNull(temperature, "climate.temperature");
        Objects.requireNonNull(moisture, "climate.moisture");
        Checks.inRange("climate.noise_cells", noiseCells, 1, ContinentsDef.MAX_NOISE_CELLS);
        Checks.inRange("climate.polar_below", polarBelow, 0, MAX_VALUE);
        Checks.inRange("climate.boreal_below", borealBelow, 0, MAX_VALUE);
        Checks.inRange("climate.tropical_from", tropicalFrom, 0, MAX_VALUE);
        Checks.inRange("climate.arid_below", aridBelow, 0, MAX_VALUE);
        if (borealBelow <= polarBelow) {
            throw outOfOrder("climate.boreal_below", borealBelow);
        }
        if (tropicalFrom <= borealBelow) {
            throw outOfOrder("climate.tropical_from", tropicalFrom);
        }
        zones = checkZones(zones);
        covers = checkCovers(covers);
    }

    /** Пояс провінції з температурою {@code temperature} і вологою {@code moisture}. */
    public Climate climate(int temperature, int moisture) {
        Checks.inRange("temperature", temperature, 0, MAX_VALUE);
        Checks.inRange("moisture", moisture, 0, MAX_VALUE);
        if (temperature < polarBelow) {
            return Climate.POLAR;
        }
        if (temperature < borealBelow) {
            return Climate.BOREAL;
        }
        if (moisture < aridBelow) {
            return Climate.ARID;
        }
        return temperature >= tropicalFrom ? Climate.TROPICAL : Climate.TEMPERATE;
    }

    /** Перший у порядку контенту покрив, чия умова виконана; порожньо — рельєф лишається без покриву. */
    public Optional<Cover> cover(Climate climate, Relief relief, int moisture, int height) {
        for (CoverDef cover : covers) {
            if (cover.matches(climate, relief, moisture, height)) {
                return Optional.of(cover.cover());
            }
        }
        return Optional.empty();
    }

    public ClimateZoneDef zone(Climate climate) {
        return zones.get(climate.ordinal());
    }

    public CoverDef coverDef(Cover cover) {
        return covers.stream().filter(def -> def.cover() == cover).findFirst().orElseThrow();
    }

    private static List<ClimateZoneDef> checkZones(List<ClimateZoneDef> zones) {
        List<ClimateZoneDef> copy = List.copyOf(Objects.requireNonNull(zones, "climate.zones"));
        TreeMap<Climate, ClimateZoneDef> byClimate = new TreeMap<>();
        for (ClimateZoneDef zone : copy) {
            if (byClimate.putIfAbsent(zone.climate(), zone) != null) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID,
                        ErrorDetails.of(
                                "field", "climate.id", "value", zone.climate().key()));
            }
        }
        Defs.complete("climate.zones", byClimate, List.of(Climate.values()), Climate::key);
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).climate().ordinal() != i) {
                throw outOfOrder("climate.id", copy.get(i).climate().key());
            }
        }
        return copy;
    }

    private static List<CoverDef> checkCovers(List<CoverDef> covers) {
        List<CoverDef> copy = List.copyOf(Objects.requireNonNull(covers, "climate.covers"));
        TreeMap<Cover, CoverDef> byCover = new TreeMap<>();
        for (CoverDef cover : copy) {
            if (byCover.putIfAbsent(cover.cover(), cover) != null) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID,
                        ErrorDetails.of(
                                "field", "cover.id", "value", cover.cover().key()));
            }
        }
        Defs.complete("climate.covers", byCover, new TreeSet<>(List.of(Cover.values())), Cover::key);
        return copy;
    }

    private static ValidationException outOfOrder(String field, Object value) {
        return new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", field, "value", value));
    }
}
