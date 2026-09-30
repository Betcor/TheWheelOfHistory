package kolo.engine.state;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.error.Checks;
import kolo.engine.wheel.RollRecord;

/**
 * Стан світу — усе, що рушій отримує на вхід ходу й повертає на виході. Змінний: рушій змінює чернетку
 * ({@link #deepCopy()}), а не збережений стан. Колекції — лише впорядковані ({@link TreeMap}), тож обхід однаковий
 * на будь-якій JVM.
 *
 * <p>Id сутностей, які створюють правила (держави, релігії, люди, згодом армії й угоди), беруться зі спільного
 * лічильника {@link #nextIdSeq()}; id провінцій і морських зон — номери карти, бо карта не змінюється.
 */
public final class WorldState {

    /** Версія схеми стану; зміна структури підвищує її й додає міграцію снапшота. */
    public static final int SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final String contentHash;
    private final long seed;
    private int turn;
    private long nextIdSeq;
    private final GameMap map;
    private final TreeMap<CountryId, Country> countries;
    private final TreeMap<ProvinceId, Province> provinces;
    private final TreeMap<PersonId, Person> people;
    private final TreeMap<ReligionId, Religion> religions;
    private final List<RollRecord> generationRolls;

    /**
     * @param contentHash хеш контенту, з яким створено світ; клієнт і сервер звіряють його
     * @param seed seed світу
     * @param turn хід; рік = 1970 + turn
     * @param nextIdSeq наступний номер спільного лічильника id
     * @param generationRolls колеса генерації світу (карта, релігії, святі центри) в порядку кидків; колеса держав —
     *     у державах
     */
    public WorldState(
            int schemaVersion,
            String contentHash,
            long seed,
            int turn,
            long nextIdSeq,
            GameMap map,
            Map<CountryId, Country> countries,
            Map<ProvinceId, Province> provinces,
            Map<PersonId, Person> people,
            Map<ReligionId, Religion> religions,
            List<RollRecord> generationRolls) {
        this.schemaVersion = Checks.inRange("schema_version", schemaVersion, 1, Integer.MAX_VALUE);
        this.contentHash = Checks.notBlank("content_hash", contentHash);
        this.seed = seed;
        this.turn = Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
        this.nextIdSeq = Checks.inRange("next_id_seq", nextIdSeq, 0, Long.MAX_VALUE);
        this.map = Objects.requireNonNull(map, "map");
        this.countries = new TreeMap<>(countries);
        this.provinces = new TreeMap<>(provinces);
        this.people = new TreeMap<>(people);
        this.religions = new TreeMap<>(religions);
        this.generationRolls = List.copyOf(generationRolls);
    }

    /** Глибока копія для чернетки ходу. Незмінні частини (карта, релігії, записи коліс) спільні з оригіналом. */
    public WorldState deepCopy() {
        TreeMap<CountryId, Country> countryCopies = new TreeMap<>();
        countries.forEach((id, country) -> countryCopies.put(id, country.copy()));
        TreeMap<ProvinceId, Province> provinceCopies = new TreeMap<>();
        provinces.forEach((id, province) -> provinceCopies.put(id, province.copy()));
        TreeMap<PersonId, Person> personCopies = new TreeMap<>();
        people.forEach((id, person) -> personCopies.put(id, person.copy()));
        return new WorldState(
                schemaVersion,
                contentHash,
                seed,
                turn,
                nextIdSeq,
                map,
                countryCopies,
                provinceCopies,
                personCopies,
                religions,
                generationRolls);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String contentHash() {
        return contentHash;
    }

    public long seed() {
        return seed;
    }

    /** Хід; рік = 1970 + turn. */
    public int turn() {
        return turn;
    }

    public void setTurn(int turn) {
        this.turn = Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
    }

    /** Наступний номер спільного лічильника id. */
    public long nextIdSeq() {
        return nextIdSeq;
    }

    /** Видає номер для нової сутності й зсуває лічильник. */
    public long takeId() {
        return nextIdSeq++;
    }

    public GameMap map() {
        return map;
    }

    /** Держави за id; змінна мапа стану. */
    public SortedMap<CountryId, Country> countries() {
        return countries;
    }

    /** Провінції — комірки суходолу — за id; змінна мапа стану. */
    public SortedMap<ProvinceId, Province> provinces() {
        return provinces;
    }

    /** Відомі люди за id; змінна мапа стану. */
    public SortedMap<PersonId, Person> people() {
        return people;
    }

    /** Релігії за id; змінна мапа стану. */
    public SortedMap<ReligionId, Religion> religions() {
        return religions;
    }

    /** Колеса генерації світу в порядку кидків. */
    public List<RollRecord> generationRolls() {
        return generationRolls;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorldState that
                && schemaVersion == that.schemaVersion
                && contentHash.equals(that.contentHash)
                && seed == that.seed
                && turn == that.turn
                && nextIdSeq == that.nextIdSeq
                && map.equals(that.map)
                && countries.equals(that.countries)
                && provinces.equals(that.provinces)
                && people.equals(that.people)
                && religions.equals(that.religions)
                && generationRolls.equals(that.generationRolls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentHash, seed, turn, nextIdSeq, countries.keySet());
    }

    @Override
    public String toString() {
        return "WorldState[seed=" + seed + ", turn=" + turn + ", countries=" + countries.size() + "]";
    }
}
