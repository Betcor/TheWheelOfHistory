package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.PowerComponent;
import kolo.engine.content.StreakKind;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.state.FateTokens;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.WheelKind;

/**
 * Ланцюжок коліс генерації держави (GD §4.1).
 *
 * <p>Порядок: материк і площа (їх уже обрало розміщення держав на карті, {@link WorldMap#placement()}) → географія →
 * населення → лад → релігія → розвиненість → ВВП → ІЛР → розмір армії → вишкіл армії → ресурси → ядерний статус →
 * передісторія → назва → відомі люди. Назва крутиться перед людьми: людей звуть у мовному стилі назви, а обидва
 * колеса нейтральні для стріків, тож порядок на результат гри не впливає.
 *
 * <p>Кожне колесо бачить усе, що дали попередні: мітки держави й модифікатори, що вже діють (ладу, релігії, нагород
 * стріків). Населення зсуває розвиненість і ВВП, вихід до моря — ВВП; родовища з провінцій держави йдуть у колесо
 * ядерного статусу (уран), сусіди по суходолу — у передісторію. Якість коліс площі, населення, розвиненості, ВВП, ІЛР,
 * армії, ядерного статусу й передісторії йде в лічильник стріків ({@link Streaks}); материк, ресурси, лад, релігія,
 * назва й люди нейтральні. Коли стрік спрацьовує, колесо стріку ({@link StreakWheel}) крутиться одразу, і його
 * нагорода діє на наступні колеса: мітки — на передісторію й людей, модифікатори — на перевагу, додаткові постаті — на
 * колесо людей. Жетони долі нагород складаються й обрізаються до {@link FateTokens#MAX}.
 *
 * <p>Бюджет сили (GD §4.11, {@link PowerBudget}): після кожного колеса площі, населення, розвиненості, ВВП, ІЛР,
 * армії й ядерного статусу проміжна сила перераховується; якщо вона поза коридором хоста, наступні з цих коліс
 * отримують перевагу до середини. Зсув — лише для коліс генерації, у модифікатори держави не йде.
 *
 * <p>Модифікатори фрагментів передісторії стають модифікаторами держави.
 */
public final class CountryGenerator {

    private CountryGenerator() {}

    /**
     * @param rng окремий потік генерації держави; розгалужується за колесами ({@code population}, {@code regime},
     *     {@code religion}, {@code development}, {@code gdp}, {@code hdi}, {@code army_size}, {@code army_training},
     *     {@code resources}, {@code nuclear}, {@code backstory}, {@code name}, {@code people}) і стріками ({@code
     *     streak:<вид>}), тож зміна одного колеса не зсуває інших; колеса материка й площі крутило розміщення
     * @throws InvariantViolationException якщо якесь колесо не має жодного сектора або не вдалося скласти вільну
     *     назву чи ім'я
     * @throws ValidationException якщо карта не узгоджується з контентом (рівня площі чи провінції немає)
     */
    public static StartCountry generate(Rng rng, ContentPack content, CountryGenerationInput input) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(input, "input");
        Chain chain = new Chain(rng, content, PowerBudget.start(input.corridor(), input.npc()));
        WorldMap map = input.map();

        PlacedCountry territory = input.territory();
        AreaLevelDef area = content.map()
                .placement()
                .area(territory.area())
                .orElseThrow(() -> new ValidationException(
                        ErrorCode.UNKNOWN_REFERENCE,
                        ErrorDetails.of(
                                "field", "area", "value", territory.area().value())));
        chain.rated(territory.tags(), area.quality(), territory.rolls());
        chain.power(PowerComponent.AREA, area.quality());
        StartGeography geography =
                Geography.generate(content, territory.cells(), map.sea(), map.climate(), map.fertility());
        chain.tags.addAll(geography.tags());
        StartPopulation population = PopulationWheel.generate(
                rng.fork("population"),
                content,
                chain.modifiers(PopulationWheel.KIND),
                territory.area(),
                geography,
                map.fertility());
        chain.rated(population.tags(), population.quality(), population.rolls());
        chain.power(PowerComponent.POPULATION, population.quality());

        Regime regime = RegimeWheel.generate(rng.fork("regime"), content);
        chain.neutral(regime.tags(), regime.modifiers(), regime.rolls());
        StartStateReligion religion =
                StateReligionWheel.generate(rng.fork("religion"), content, chain.tags, input.religions());
        chain.neutral(religion.tags(), religion.modifiers(), religion.rolls());

        StartDevelopment development = DevelopmentWheel.generate(
                rng.fork("development"),
                content,
                chain.modifiers(Arrays.stream(TechBranch.values())
                        .map(DevelopmentWheel::kind)
                        .toArray(WheelKind[]::new)),
                population);
        chain.rated(development.tags(), development.quality(), development.rolls());
        chain.power(PowerComponent.DEVELOPMENT, development.quality());
        StartGdp gdp = GdpWheel.generate(
                rng.fork("gdp"), content, chain.modifiers(GdpWheel.KIND), development, population, geography);
        chain.rated(gdp.tags(), gdp.quality(), gdp.rolls());
        chain.power(PowerComponent.GDP, gdp.quality());
        StartHdi hdi = HdiWheel.generate(rng.fork("hdi"), content, chain.modifiers(HdiWheel.KIND), gdp);
        chain.rated(hdi.tags(), hdi.quality(), hdi.rolls());
        chain.power(PowerComponent.HDI, hdi.quality());
        StartArmySize armySize =
                ArmySizeWheel.generate(rng.fork("army_size"), content, chain.modifiers(ArmySizeWheel.KIND), gdp);
        chain.rated(armySize.tags(), armySize.quality(), armySize.rolls());
        chain.power(PowerComponent.ARMY_SIZE, armySize.quality());
        StartArmyTraining armyTraining = ArmyTrainingWheel.generate(
                rng.fork("army_training"), content, chain.modifiers(ArmyTrainingWheel.KIND), gdp, development);
        chain.rated(armyTraining.tags(), armyTraining.quality(), armyTraining.rolls());
        chain.power(PowerComponent.ARMY_TRAINING, armyTraining.quality());
        StartResources resources =
                ResourceWheel.generate(rng.fork("resources"), content, map.suitability(), territory.cells());
        chain.rolls.addAll(resources.rolls());
        StartNuclear nuclear = NuclearWheel.generate(
                rng.fork("nuclear"), content, chain.modifiers(NuclearWheel.KIND), development, resources.resources());
        chain.rated(nuclear.tags(), nuclear.quality(), nuclear.rolls());
        chain.power(PowerComponent.NUCLEAR, nuclear.quality());

        Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), content, chain.tags, input.neighbors());
        chain.rolls.addAll(backstory.rolls());
        chain.tags.addAll(backstory.tags());
        backstory.quality().ifPresent(chain::quality);

        StartName name = NameWheel.generate(
                rng.fork("name"), content, regime.subIdeology().id(), input.takenCountryNames());
        chain.rolls.add(name.roll());
        StartPeople people = PeopleWheel.generate(
                rng.fork("people"), content, chain.tags, name.style(), input.takenPersonNames(), chain.extraPeople());
        chain.rolls.addAll(people.rolls());

        // Модифікатори передісторії не діють на колеса генерації: після неї лишаються лише нейтральні колеса.
        List<Modifier> modifiers = new ArrayList<>(chain.modifiers);
        modifiers.addAll(backstory.modifiers());
        return new StartCountry(
                territory,
                geography,
                population,
                regime,
                religion,
                development,
                gdp,
                hdi,
                armySize,
                armyTraining,
                resources,
                nuclear,
                backstory,
                chain.streaks,
                chain.power,
                name,
                people,
                chain.tags,
                modifiers,
                chain.fateTokens(),
                chain.rolls);
    }

    /** Те, що ланцюжок накопичує від колеса до колеса. */
    private static final class Chain {

        private final Rng rng;
        private final ContentPack content;
        private final TreeSet<String> tags = new TreeSet<>();
        private final List<Modifier> modifiers = new ArrayList<>();
        private final List<RollRecord> rolls = new ArrayList<>();
        private final List<StreakBonus> streaks = new ArrayList<>();
        private Streaks counter = Streaks.START;
        private PowerBudget power;

        Chain(Rng rng, ContentPack content, PowerBudget power) {
            this.rng = rng;
            this.content = content;
            this.power = power;
        }

        /** Модифікатори, що діють на наступне колесо {@code kinds}: держави й зсув коридору сили. */
        List<Modifier> modifiers(WheelKind... kinds) {
            List<Modifier> result = new ArrayList<>(modifiers);
            result.addAll(power.modifiers(List.of(kinds)));
            return List.copyOf(result);
        }

        void power(PowerComponent component, int quality) {
            power = power.add(content.balance(), component, quality);
        }

        /** Колесо, що не рахується в стріки. */
        void neutral(Collection<String> wheelTags, List<Modifier> wheelModifiers, List<RollRecord> wheelRolls) {
            tags.addAll(wheelTags);
            modifiers.addAll(wheelModifiers);
            rolls.addAll(wheelRolls);
        }

        /** Колесо, якість якого йде в лічильник стріків. */
        void rated(Collection<String> wheelTags, int wheelQuality, List<RollRecord> wheelRolls) {
            tags.addAll(wheelTags);
            rolls.addAll(wheelRolls);
            quality(wheelQuality);
        }

        void quality(int wheelQuality) {
            Streaks.Step step = counter.next(content.balance().streaks(), wheelQuality);
            counter = step.streaks();
            step.triggered().ifPresent(this::streak);
        }

        private void streak(StreakKind kind) {
            StreakBonus bonus = StreakWheel.generate(rng.fork("streak:" + kind.key()), content, kind);
            streaks.add(bonus);
            tags.addAll(bonus.tags());
            modifiers.addAll(bonus.modifiers());
            rolls.add(bonus.roll());
        }

        int extraPeople() {
            return streaks.stream().mapToInt(StreakBonus::extraPeople).sum();
        }

        int fateTokens() {
            return Math.min(
                    FateTokens.MAX,
                    streaks.stream().mapToInt(StreakBonus::fateTokens).sum());
        }
    }
}
