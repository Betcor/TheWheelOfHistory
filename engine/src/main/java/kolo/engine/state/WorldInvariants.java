package kolo.engine.state;

import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;

/**
 * Інваріанти стану світу (перевіряються після генерації й після кожного ходу). Порушення — баг рушія, а не помилка
 * гравця, тож перевірка кидає {@link InvariantViolationException} з назвою правила в {@code check}.
 */
public final class WorldInvariants {

    private WorldInvariants() {}

    /** @throws InvariantViolationException з першим порушеним правилом */
    public static void check(WorldState state) {
        require(state.schemaVersion() == WorldState.SCHEMA_VERSION, "schema_version", state.schemaVersion());
        checkProvinces(state);
        checkCountries(state);
        checkPeople(state);
        checkReligions(state);
        checkIdSequence(state);
    }

    private static void checkProvinces(WorldState state) {
        GameMap map = state.map();
        TreeSet<ProvinceId> land = new TreeSet<>();
        for (int n = 0; n < map.tiles().size(); n++) {
            if (map.tile(n).isLand()) {
                land.add(ProvinceId.of(n));
            }
        }
        require(
                land.equals(state.provinces().keySet()),
                "provinces_are_land",
                state.provinces().size());
        for (Map.Entry<ProvinceId, Province> entry : state.provinces().entrySet()) {
            Province province = entry.getValue();
            require(
                    entry.getKey().equals(province.id()),
                    "province_key",
                    province.id().value());
            requireCountry(state, province.owner(), "province_owner", province.id());
            requireCountry(state, province.controller(), "province_controller", province.id());
        }
    }

    private static void checkCountries(WorldState state) {
        for (Map.Entry<CountryId, Country> entry : state.countries().entrySet()) {
            Country country = entry.getValue();
            String id = country.id().value();
            require(entry.getKey().equals(country.id()), "country_key", id);
            Province capital = state.provinces().get(country.capital());
            require(capital != null && capital.owner().equals(Optional.of(country.id())), "capital_owned", id);
            country.religion()
                    .ifPresent(religion -> require(state.religions().containsKey(religion), "country_religion", id));
            require(
                    country.startDevelopment().size() == TechBranch.values().length
                            && country.startDevelopment().values().stream()
                                    .allMatch(level -> level >= Development.MIN && level <= Development.MAX),
                    "start_development",
                    id);
            require(country.hdi() >= Stat.HDI.min() && country.hdi() <= Stat.HDI.max(), "country_hdi", id);
            require(country.armyShareBp() >= 0 && country.armyShareBp() <= 10_000, "army_share_bp", id);
            require(country.training() >= Training.MIN && country.training() <= Training.MAX, "training", id);
            require(
                    country.nuclear() == NuclearStatus.ARSENAL ? country.warheads() > 0 : country.warheads() == 0,
                    "warheads",
                    id);
            require(country.fateTokens() >= 0 && country.fateTokens() <= FateTokens.MAX, "fate_tokens", id);
            for (PersonId person : country.people()) {
                Person found = state.people().get(person);
                require(found != null && found.country().equals(country.id()), "country_people", person.value());
            }
            require(new TreeSet<>(country.people()).size() == country.people().size(), "country_people_unique", id);
        }
    }

    private static void checkPeople(WorldState state) {
        for (Map.Entry<PersonId, Person> entry : state.people().entrySet()) {
            Person person = entry.getValue();
            require(
                    entry.getKey().equals(person.id()),
                    "person_key",
                    person.id().value());
            Country country = state.countries().get(person.country());
            require(
                    country != null && country.people().contains(person.id()),
                    "person_country",
                    person.id().value());
        }
    }

    private static void checkReligions(WorldState state) {
        for (Map.Entry<ReligionId, Religion> entry : state.religions().entrySet()) {
            Religion religion = entry.getValue();
            require(
                    entry.getKey().equals(religion.id()),
                    "religion_key",
                    religion.id().value());
            require(
                    state.provinces().containsKey(religion.holyCenter()),
                    "holy_center",
                    religion.id().value());
        }
    }

    // Держави, люди й релігії беруть номери з одного лічильника: номери не повторюються й менші за лічильник.
    private static void checkIdSequence(WorldState state) {
        TreeSet<Long> numbers = new TreeSet<>();
        state.countries().keySet().forEach(id -> numbers.add(id.number()));
        state.people().keySet().forEach(id -> numbers.add(id.number()));
        state.religions().keySet().forEach(id -> numbers.add(id.number()));
        int issued = state.countries().size()
                + state.people().size()
                + state.religions().size();
        require(numbers.size() == issued, "id_seq_unique", issued);
        require(numbers.isEmpty() || state.nextIdSeq() > numbers.last(), "next_id_seq", state.nextIdSeq());
    }

    private static void requireCountry(WorldState state, Optional<CountryId> country, String check, ProvinceId at) {
        country.ifPresent(id -> require(state.countries().containsKey(id), check, at.value()));
    }

    private static void require(boolean condition, String check, Object value) {
        if (!condition) {
            throw new InvariantViolationException(ErrorDetails.of("check", check, "value", String.valueOf(value)));
        }
    }
}
