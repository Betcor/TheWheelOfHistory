package kolo.engine.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kolo.engine.content.ResourceId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Country;
import kolo.engine.state.CountryId;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Person;
import kolo.engine.state.PersonId;
import kolo.engine.state.Province;
import kolo.engine.state.Religion;
import kolo.engine.state.WorldState;

/** Картка держави гравця зі стану світу — лише для її власника (туман війни, GD §16.1). */
public final class CountryCards {

    private CountryCards() {}

    /** @throws ValidationException якщо держави з таким номером немає */
    public static CountryCard of(WorldState state, int number) {
        Objects.requireNonNull(state, "state");
        Country country = state.countries().get(CountryId.of(number));
        if (country == null) {
            throw new ValidationException(
                    ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", "country", "value", number));
        }
        int provinces = 0;
        long populationK = 0;
        List<CountryCard.Deposit> deposits = new ArrayList<>();
        for (Province province : state.provinces().values()) {
            if (!province.owner().equals(Optional.of(country.id()))) {
                continue;
            }
            provinces++;
            populationK += province.populationK();
            int cell = Math.toIntExact(province.id().number());
            for (ResourceId resource : province.deposits()) {
                deposits.add(new CountryCard.Deposit(cell, resource));
            }
        }
        List<CountryCard.PersonCard> people = new ArrayList<>(country.people().size());
        for (PersonId id : country.people()) {
            Person person = state.people().get(id);
            people.add(new CountryCard.PersonCard(
                    person.name(), person.kind(), person.sex(), person.traits(), person.bornTurn(), person.alive()));
        }
        Optional<Religion> religion =
                country.religion().map(id -> state.religions().get(id));
        // Порядок генерації — за номером id, а не за рядком id ("rel_10" < "rel_2").
        List<NounPhrase> worldReligions = state.religions().values().stream()
                .sorted(Comparator.comparingLong(r -> r.id().number()))
                .map(Religion::name)
                .toList();
        return new CountryCard(
                number,
                country.name(),
                Math.toIntExact(country.capital().number()),
                provinces,
                populationK,
                country.ideology(),
                country.subIdeology(),
                religion,
                worldReligions,
                country.startDevelopment(),
                country.gdpPerCapita(),
                country.hdi(),
                country.armyShareBp(),
                country.training(),
                country.nuclear(),
                country.warheads(),
                country.fateTokens(),
                deposits,
                people,
                country.origin(),
                country.modifiers(),
                country.tags(),
                country.generationRolls());
    }
}
