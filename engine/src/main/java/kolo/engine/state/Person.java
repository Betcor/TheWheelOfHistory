package kolo.engine.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kolo.engine.content.TraitId;

/** Відома людина (GD §12). Роль і лояльність — разом із системою людей. */
public final class Person {

    private final PersonId id;
    private CountryId country;
    private final LocalizedName name;
    private final PersonKind kind;
    private final Sex sex;
    private final List<TraitId> traits;
    private final int bornTurn;
    private boolean alive;

    /**
     * @param country держава, якій служить людина
     * @param traits риси в порядку набуття
     * @param bornTurn хід народження; від'ємний — до 1970 року
     */
    public Person(
            PersonId id,
            CountryId country,
            LocalizedName name,
            PersonKind kind,
            Sex sex,
            List<TraitId> traits,
            int bornTurn,
            boolean alive) {
        this.id = Objects.requireNonNull(id, "id");
        this.country = Objects.requireNonNull(country, "country");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sex = Objects.requireNonNull(sex, "sex");
        this.traits = new ArrayList<>(traits);
        this.bornTurn = bornTurn;
        this.alive = alive;
    }

    /** Глибока копія. */
    public Person copy() {
        return new Person(id, country, name, kind, sex, traits, bornTurn, alive);
    }

    public PersonId id() {
        return id;
    }

    public CountryId country() {
        return country;
    }

    public void setCountry(CountryId country) {
        this.country = Objects.requireNonNull(country, "country");
    }

    public LocalizedName name() {
        return name;
    }

    public PersonKind kind() {
        return kind;
    }

    public Sex sex() {
        return sex;
    }

    /** Риси в порядку набуття; змінний список стану. */
    public List<TraitId> traits() {
        return traits;
    }

    public int bornTurn() {
        return bornTurn;
    }

    /** Вік у ході {@code turn}. */
    public int age(int turn) {
        return turn - bornTurn;
    }

    public boolean alive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Person that
                && id.equals(that.id)
                && country.equals(that.country)
                && name.equals(that.name)
                && kind == that.kind
                && sex == that.sex
                && traits.equals(that.traits)
                && bornTurn == that.bornTurn
                && alive == that.alive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, country, name, kind, sex, traits, bornTurn, alive);
    }

    @Override
    public String toString() {
        return "Person[" + id + ", " + name.fullName().nominative() + "]";
    }
}
