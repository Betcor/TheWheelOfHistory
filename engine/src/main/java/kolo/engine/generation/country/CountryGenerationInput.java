package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.state.CountryId;
import kolo.engine.state.PowerCorridor;

/**
 * Світ, у якому генерується держава: те, що {@link CountryGenerator} бере не з коліс держави, а ззовні.
 *
 * @param map карта світу з розміщеними державами
 * @param country номер держави в {@link WorldMap#placement()}; її ідентифікатор — {@code CountryId.of(country)}
 * @param religions релігії світу в порядку генерації (GD §25.1)
 * @param corridor коридор бюджету сили, який задав хост (GD §3.4, §4.11)
 * @param takenCountryNames повні назви держав у називному відмінку, вже зайняті в світі
 * @param takenPersonNames повні імена людей у називному відмінку, вже зайняті в світі
 */
public record CountryGenerationInput(
        WorldMap map,
        int country,
        List<StartReligion> religions,
        PowerCorridor corridor,
        SortedSet<String> takenCountryNames,
        SortedSet<String> takenPersonNames) {

    public CountryGenerationInput {
        Objects.requireNonNull(map, "map");
        Checks.inRange("country", country, 0, map.countries() - 1);
        religions = List.copyOf(religions);
        Objects.requireNonNull(corridor, "corridor");
        takenCountryNames = Collections.unmodifiableSortedSet(new TreeSet<>(takenCountryNames));
        takenPersonNames = Collections.unmodifiableSortedSet(new TreeSet<>(takenPersonNames));
    }

    /** Держава {@code country} світу з цими релігіями, без зайнятих назв, з коридором {@link PowerCorridor#DEFAULT}. */
    public static CountryGenerationInput of(WorldMap map, int country, List<StartReligion> religions) {
        return new CountryGenerationInput(
                map, country, religions, PowerCorridor.DEFAULT, new TreeSet<>(), new TreeSet<>());
    }

    /** Чи держава NPC: гравці розміщуються першими (GD §3.6), решта — NPC. */
    public boolean npc() {
        return country >= map.size().players();
    }

    /** Територія держави. */
    public PlacedCountry territory() {
        return map.country(country);
    }

    /** Сусіди держави по суходолу — кандидати для передісторії. */
    public SortedSet<CountryId> neighbors() {
        TreeSet<CountryId> ids = new TreeSet<>();
        map.neighbors(country).forEach(neighbor -> ids.add(CountryId.of(neighbor)));
        return Collections.unmodifiableSortedSet(ids);
    }
}
