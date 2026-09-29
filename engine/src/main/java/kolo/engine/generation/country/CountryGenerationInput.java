package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.ResourceId;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.state.CountryId;

/**
 * Світ, у якому генерується держава: те, що {@link CountryGenerator} бере не з коліс держави, а ззовні.
 *
 * <p>Ресурси й сусіди дає карта; доки коліс карти немає, їх передають вручну.
 *
 * @param religions релігії світу в порядку генерації (GD §25.1)
 * @param resources ресурси держави; кожен має бути в контенті, порядок і повтори не важливі
 * @param neighbors держави, з якими передісторія може пов'язати цю; порядок не важливий
 * @param takenCountryNames повні назви держав у називному відмінку, вже зайняті в світі
 * @param takenPersonNames повні імена людей у називному відмінку, вже зайняті в світі
 */
public record CountryGenerationInput(
        List<StartReligion> religions,
        SortedSet<ResourceId> resources,
        SortedSet<CountryId> neighbors,
        SortedSet<String> takenCountryNames,
        SortedSet<String> takenPersonNames) {

    public CountryGenerationInput {
        religions = List.copyOf(religions);
        resources = Collections.unmodifiableSortedSet(new TreeSet<>(resources));
        neighbors = Collections.unmodifiableSortedSet(new TreeSet<>(neighbors));
        takenCountryNames = Collections.unmodifiableSortedSet(new TreeSet<>(takenCountryNames));
        takenPersonNames = Collections.unmodifiableSortedSet(new TreeSet<>(takenPersonNames));
    }

    /** Світ з цими релігіями, без ресурсів, сусідів і зайнятих назв. */
    public static CountryGenerationInput of(List<StartReligion> religions) {
        return new CountryGenerationInput(
                religions, new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), new TreeSet<>());
    }
}
