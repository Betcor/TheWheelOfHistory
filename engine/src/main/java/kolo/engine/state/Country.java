package kolo.engine.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.error.Checks;
import kolo.engine.modifier.Modifier;
import kolo.engine.wheel.RollRecord;

/**
 * Держава. Поки лише те, що дає генерація (GD §4); показники економіки (стабільність, вплив, легітимність, казна,
 * бюджет) з'являться разом з економікою й новою версією схеми стану.
 *
 * <p>Базові значення зберігаються без модифікаторів: ефективні рахує {@link kolo.engine.modifier.Modifiers}.
 */
public final class Country {

    private final CountryId id;
    private ControlType control;
    private LocalizedName name;
    private final NameStyleId nameStyle;
    private ProvinceId capital;
    private IdeologyId ideology;
    private SubIdeologyId subIdeology;
    private ReligionId religion;
    private final EnumMap<TechBranch, Integer> startDevelopment;
    private int gdpPerCapita;
    private int hdi;
    private int armyShareBp;
    private int training;
    private NuclearStatus nuclear;
    private int warheads;
    private int fateTokens;
    private final List<Modifier> modifiers;
    private final TreeSet<String> tags;
    private final List<PersonId> people;
    private final CountryOrigin origin;
    private final List<RollRecord> generationRolls;

    /**
     * @param nameStyle мовний стиль назви — ним звуть людей держави
     * @param religion державна релігія; {@code null} — світська держава
     * @param startDevelopment розвиненість галузей на 1970 рік, кожна галузь ({@link Development})
     * @param gdpPerCapita ВВП на душу, умовні долари 1970 року
     * @param hdi ІЛР ({@link Stat#HDI})
     * @param armyShareBp частка населення під зброєю, bp
     * @param training рівень вишколу армії ({@link Training})
     * @param warheads боєголовки; лише в арсеналу
     * @param fateTokens жетони долі {@code 0..}{@value FateTokens#MAX}
     * @param modifiers модифікатори в порядку набуття
     * @param people відомі люди в порядку появи
     * @param generationRolls записи коліс генерації в порядку кидків
     */
    public Country(
            CountryId id,
            ControlType control,
            LocalizedName name,
            NameStyleId nameStyle,
            ProvinceId capital,
            IdeologyId ideology,
            SubIdeologyId subIdeology,
            ReligionId religion,
            Map<TechBranch, Integer> startDevelopment,
            int gdpPerCapita,
            int hdi,
            int armyShareBp,
            int training,
            NuclearStatus nuclear,
            int warheads,
            int fateTokens,
            List<Modifier> modifiers,
            SortedSet<String> tags,
            List<PersonId> people,
            CountryOrigin origin,
            List<RollRecord> generationRolls) {
        this.id = Objects.requireNonNull(id, "id");
        this.control = Objects.requireNonNull(control, "control");
        this.name = Objects.requireNonNull(name, "name");
        this.nameStyle = Objects.requireNonNull(nameStyle, "nameStyle");
        this.capital = Objects.requireNonNull(capital, "capital");
        this.ideology = Objects.requireNonNull(ideology, "ideology");
        this.subIdeology = Objects.requireNonNull(subIdeology, "subIdeology");
        this.religion = religion;
        this.startDevelopment = new EnumMap<>(TechBranch.class);
        this.startDevelopment.putAll(startDevelopment);
        this.gdpPerCapita = Checks.inRange("gdp_per_capita", gdpPerCapita, 0, Integer.MAX_VALUE);
        this.hdi = hdi;
        this.armyShareBp = armyShareBp;
        this.training = training;
        this.nuclear = Objects.requireNonNull(nuclear, "nuclear");
        this.warheads = warheads;
        this.fateTokens = fateTokens;
        this.modifiers = new ArrayList<>(modifiers);
        this.tags = new TreeSet<>(tags);
        this.people = new ArrayList<>(people);
        this.origin = Objects.requireNonNull(origin, "origin");
        this.generationRolls = List.copyOf(generationRolls);
    }

    /** Глибока копія: чернетка ходу змінює її, не чіпаючи оригінал. Записи й модифікатори — незмінні значення. */
    public Country copy() {
        return new Country(
                id,
                control,
                name,
                nameStyle,
                capital,
                ideology,
                subIdeology,
                religion,
                startDevelopment,
                gdpPerCapita,
                hdi,
                armyShareBp,
                training,
                nuclear,
                warheads,
                fateTokens,
                modifiers,
                tags,
                people,
                origin,
                generationRolls);
    }

    public CountryId id() {
        return id;
    }

    public ControlType control() {
        return control;
    }

    public void setControl(ControlType control) {
        this.control = Objects.requireNonNull(control, "control");
    }

    public LocalizedName name() {
        return name;
    }

    public void setName(LocalizedName name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /** Мовний стиль назви: ним звуть людей держави. */
    public NameStyleId nameStyle() {
        return nameStyle;
    }

    public ProvinceId capital() {
        return capital;
    }

    public void setCapital(ProvinceId capital) {
        this.capital = Objects.requireNonNull(capital, "capital");
    }

    public IdeologyId ideology() {
        return ideology;
    }

    public SubIdeologyId subIdeology() {
        return subIdeology;
    }

    /** Змінює лад: ідеологію й підкласифікацію разом. */
    public void setRegime(IdeologyId ideology, SubIdeologyId subIdeology) {
        this.ideology = Objects.requireNonNull(ideology, "ideology");
        this.subIdeology = Objects.requireNonNull(subIdeology, "subIdeology");
    }

    /** Державна релігія; порожньо — світська держава. */
    public Optional<ReligionId> religion() {
        return Optional.ofNullable(religion);
    }

    public void setReligion(ReligionId religion) {
        this.religion = religion;
    }

    /** Розвиненість галузей на 1970 рік (GD §4.3); не змінюється — далі розвиток дають технології. */
    public Map<TechBranch, Integer> startDevelopment() {
        return Collections.unmodifiableMap(startDevelopment);
    }

    /** ВВП на душу, умовні долари 1970 року. */
    public int gdpPerCapita() {
        return gdpPerCapita;
    }

    public void setGdpPerCapita(int gdpPerCapita) {
        this.gdpPerCapita = Checks.inRange("gdp_per_capita", gdpPerCapita, 0, Integer.MAX_VALUE);
    }

    /** ІЛР без модифікаторів. */
    public int hdi() {
        return hdi;
    }

    public void setHdi(int hdi) {
        this.hdi = hdi;
    }

    /** Частка населення під зброєю, bp. */
    public int armyShareBp() {
        return armyShareBp;
    }

    public void setArmyShareBp(int armyShareBp) {
        this.armyShareBp = armyShareBp;
    }

    /** Рівень вишколу армії. */
    public int training() {
        return training;
    }

    public void setTraining(int training) {
        this.training = training;
    }

    public NuclearStatus nuclear() {
        return nuclear;
    }

    public int warheads() {
        return warheads;
    }

    /** Змінює ядерний статус разом із кількістю боєголовок. */
    public void setNuclear(NuclearStatus nuclear, int warheads) {
        this.nuclear = Objects.requireNonNull(nuclear, "nuclear");
        this.warheads = warheads;
    }

    public int fateTokens() {
        return fateTokens;
    }

    public void setFateTokens(int fateTokens) {
        this.fateTokens = fateTokens;
    }

    /** Модифікатори в порядку набуття; змінний список стану. */
    public List<Modifier> modifiers() {
        return modifiers;
    }

    /** Мітки держави; змінний набір стану. */
    public SortedSet<String> tags() {
        return tags;
    }

    /** Відомі люди в порядку появи; змінний список стану. */
    public List<PersonId> people() {
        return people;
    }

    public CountryOrigin origin() {
        return origin;
    }

    /** Записи коліс генерації в порядку кидків. */
    public List<RollRecord> generationRolls() {
        return generationRolls;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Country that
                && id.equals(that.id)
                && control == that.control
                && name.equals(that.name)
                && nameStyle.equals(that.nameStyle)
                && capital.equals(that.capital)
                && ideology.equals(that.ideology)
                && subIdeology.equals(that.subIdeology)
                && Objects.equals(religion, that.religion)
                && startDevelopment.equals(that.startDevelopment)
                && gdpPerCapita == that.gdpPerCapita
                && hdi == that.hdi
                && armyShareBp == that.armyShareBp
                && training == that.training
                && nuclear == that.nuclear
                && warheads == that.warheads
                && fateTokens == that.fateTokens
                && modifiers.equals(that.modifiers)
                && tags.equals(that.tags)
                && people.equals(that.people)
                && origin.equals(that.origin)
                && generationRolls.equals(that.generationRolls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, capital, ideology, subIdeology, religion, gdpPerCapita, hdi, tags, people);
    }

    @Override
    public String toString() {
        return "Country[" + id + ", " + name.shortName().nominative() + "]";
    }
}
