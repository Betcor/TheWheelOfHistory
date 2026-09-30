package kolo.engine.state;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.ArchetypeId;
import kolo.engine.content.AspectId;
import kolo.engine.content.DogmaId;
import kolo.engine.content.FaithFormId;
import kolo.engine.content.ReligionPolityId;

/**
 * Релігія світу (GD §25). Значення: коли релігія зміниться (захоплений святий центр, розкол), стан замінює запис
 * новим.
 *
 * @param aspects аспекти божества в порядку набуття
 * @param dogmas догмати в порядку набуття
 * @param figureSex стать постаті, чиїм ім'ям зветься віра
 * @param figure ім'я постаті з формами відмінків
 * @param name назва віри з формами відмінків
 * @param tags мітки релігії
 * @param holyCenter провінція святого центру
 */
public record Religion(
        ReligionId id,
        ArchetypeId archetype,
        List<AspectId> aspects,
        List<DogmaId> dogmas,
        ReligionPolityId polity,
        FaithFormId faithForm,
        Sex figureSex,
        NounPhrase figure,
        NounPhrase name,
        SortedSet<String> tags,
        ProvinceId holyCenter) {

    public Religion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archetype, "archetype");
        aspects = List.copyOf(aspects);
        dogmas = List.copyOf(dogmas);
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(faithForm, "faithForm");
        Objects.requireNonNull(figureSex, "figureSex");
        Objects.requireNonNull(figure, "figure");
        Objects.requireNonNull(name, "name");
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        Objects.requireNonNull(holyCenter, "holyCenter");
    }
}
