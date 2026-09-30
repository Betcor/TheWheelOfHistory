package kolo.engine.state;

import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.ResourceId;
import kolo.engine.error.Checks;

/**
 * Провінція — комірка суходолу: те, що змінюється під час гри. Географія провінції незмінна й лежить у
 * {@link MapTile} карти з тим самим номером.
 */
public final class Province {

    private final ProvinceId id;
    private CountryId owner;
    private CountryId controller;
    private int populationK;
    private final TreeSet<ResourceId> deposits;
    private final TreeSet<String> tags;

    /**
     * @param owner власник; {@code null} — нічийна земля
     * @param controller хто контролює: окупант або власник; {@code null} — ніхто
     * @param populationK населення, тисячі
     */
    public Province(
            ProvinceId id,
            CountryId owner,
            CountryId controller,
            int populationK,
            SortedSet<ResourceId> deposits,
            SortedSet<String> tags) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = owner;
        this.controller = controller;
        this.populationK = Checks.inRange("population_k", populationK, 0, Integer.MAX_VALUE);
        this.deposits = new TreeSet<>(deposits);
        this.tags = new TreeSet<>(tags);
    }

    /** Глибока копія: чернетка ходу змінює її, не чіпаючи оригінал. */
    public Province copy() {
        return new Province(id, owner, controller, populationK, deposits, tags);
    }

    public ProvinceId id() {
        return id;
    }

    /** Власник; порожньо — нічийна земля. */
    public Optional<CountryId> owner() {
        return Optional.ofNullable(owner);
    }

    public void setOwner(CountryId owner) {
        this.owner = owner;
    }

    /** Хто контролює провінцію: окупант, якщо він не власник; порожньо — ніхто. */
    public Optional<CountryId> controller() {
        return Optional.ofNullable(controller);
    }

    public void setController(CountryId controller) {
        this.controller = controller;
    }

    /** Населення, тисячі. */
    public int populationK() {
        return populationK;
    }

    public void setPopulationK(int populationK) {
        this.populationK = Checks.inRange("population_k", populationK, 0, Integer.MAX_VALUE);
    }

    /** Родовища ресурсів; змінний набір стану. */
    public SortedSet<ResourceId> deposits() {
        return deposits;
    }

    /** Мітки провінції; змінний набір стану. */
    public SortedSet<String> tags() {
        return tags;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Province that
                && id.equals(that.id)
                && Objects.equals(owner, that.owner)
                && Objects.equals(controller, that.controller)
                && populationK == that.populationK
                && deposits.equals(that.deposits)
                && tags.equals(that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, owner, controller, populationK, deposits, tags);
    }

    @Override
    public String toString() {
        return "Province[" + id + ", owner=" + owner + "]";
    }
}
