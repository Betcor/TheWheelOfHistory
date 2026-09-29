package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.PersonKindDef;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.PersonNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.PersonKind;
import kolo.engine.state.Sex;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо відомих людей (GD §4.1, колесо 16: залежить від ідеології, ІЛР і передісторії; GD §4.8, §12.3).
 *
 * <ol>
 *   <li>Колесо {@link #COUNT_KIND} з рівними секторами обирає кількість постатей у межах балансу ({@code
 *       generation.notable_people}).
 *   <li>Для кожної постаті колесо {@link #KIND_KIND} обирає тип: вага сектора — {@link PersonKindDef#weightFor} за
 *       мітками держави (лад, рівні коліс генерації, передісторія); типи з нульовою вагою не беруть участі. Типи в
 *       державі можуть повторюватися.
 *   <li>Стать — рівноймовірно, вік на 1970 рік — рівноймовірно в межах {@code generation.person_age}. Це не колеса:
 *       вони не впливають на гру й не показуються гравцеві.
 *   <li>Колесо {@link #TRAIT_COUNT_KIND} з рівними секторами обирає кількість рис ({@code generation.person_traits}),
 *       колесо {@link #TRAIT_KIND} — кожну рису рівноймовірно серед доступних типу, ще не обраних і сумісних з уже
 *       обраними. Якщо таких забракло, рис менше.
 *   <li>Ім'я — у мовному стилі держави ({@link PersonNames}); ім'я, повне ім'я якого вже зайняте, перегенерується.
 * </ol>
 *
 * <p>Перевага на колеса постатей не діє: сектори мають рівень {@link OutcomeTier#PARTIAL} і нейтральну якість
 * {@value #QUALITY} — постаті не впливають на стріки генерації.
 */
public final class PeopleWheel {

    public static final WheelKind COUNT_KIND = new WheelKind("generation_people_count");
    public static final WheelKind KIND_KIND = new WheelKind("generation_person_kind");
    public static final WheelKind TRAIT_COUNT_KIND = new WheelKind("generation_person_trait_count");
    public static final WheelKind TRAIT_KIND = new WheelKind("generation_person_trait");

    /** Постаті нейтральні для держави: ні тип, ні риси не роблять старт «добрим» чи «поганим». */
    static final int QUALITY = 50;

    /**
     * Скільки разів перегенерувати ім'я, зайняте іншою постаттю. Вбудований контент дає мільйони імен на стиль, тож
     * вичерпати спроби — ознака бракованого контенту чи бага, а не невдачі.
     */
    static final int NAME_ATTEMPTS = 100;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private PeopleWheel() {}

    /**
     * @param rng окремий потік відомих людей держави; всередині розгалужується на кількість і на кожну постать, а
     *     постать — на тип, стать, вік, риси й ім'я, тож зміна одного кидка не зсуває інших
     * @param tags мітки держави з попередніх коліс генерації й передісторії
     * @param style мовний стиль назви держави: ним звуть її людей
     * @param takenNames повні імена в називному відмінку, вже зайняті в світі; нові постаті їх не повторюють і не
     *     повторюють одна одну
     * @throws ValidationException якщо стилю немає в контенті
     * @throws InvariantViolationException якщо за {@value #NAME_ATTEMPTS} спроб не вдалося скласти вільне ім'я
     */
    public static StartPeople generate(
            Rng rng, ContentPack content, Set<String> tags, NameStyleId style, Set<String> takenNames) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(style, "style");
        TreeSet<String> taken = new TreeSet<>(takenNames);

        WheelSpin<Integer> countSpin = spin(
                content,
                rng.fork("count"),
                COUNT_KIND,
                countSectors("people_", content.balance().generation().notablePeople()));
        List<Sector<PersonKind>> kinds = kindSectors(content, tags);
        List<StartPerson> people = new ArrayList<>();
        // Ваги типів не залежать від уже обраних постатей: без жодного доступного типу постатей немає зовсім.
        for (int i = 0; i < countSpin.value() && !kinds.isEmpty(); i++) {
            people.add(person(rng.fork("person:" + i), content, kinds, style, taken));
        }
        return new StartPeople(people, countSpin.record());
    }

    private static StartPerson person(
            Rng rng, ContentPack content, List<Sector<PersonKind>> kinds, NameStyleId style, TreeSet<String> taken) {
        List<RollRecord> rolls = new ArrayList<>();
        WheelSpin<PersonKind> kindSpin = spin(content, rng.fork("kind"), KIND_KIND, kinds);
        rolls.add(kindSpin.record());
        PersonKind kind = kindSpin.value();

        Sex sex = rng.fork("sex").nextInt(2) == 0 ? Sex.MALE : Sex.FEMALE;
        CountRange ages = content.balance().generation().personAge();
        int age = ages.min() + rng.fork("age").nextInt(ages.max() - ages.min() + 1);

        Rng traitRng = rng.fork("traits");
        WheelSpin<Integer> traitCount = spin(
                content,
                traitRng,
                TRAIT_COUNT_KIND,
                countSectors("traits_", content.balance().generation().personTraits()));
        rolls.add(traitCount.record());
        List<TraitId> traits = new ArrayList<>();
        for (int i = 0; i < traitCount.value(); i++) {
            List<Sector<TraitId>> sectors = traitSectors(content, kind, traits);
            if (sectors.isEmpty()) {
                break;
            }
            WheelSpin<TraitId> spin = spin(content, traitRng, TRAIT_KIND, sectors);
            rolls.add(spin.record());
            traits.add(spin.value());
        }

        LocalizedName name = name(rng.fork("name"), content, style, sex, taken);
        return new StartPerson(kind, sex, name, traits, -age, rolls);
    }

    private static LocalizedName name(Rng rng, ContentPack content, NameStyleId style, Sex sex, TreeSet<String> taken) {
        for (int attempt = 0; attempt < NAME_ATTEMPTS; attempt++) {
            LocalizedName name = PersonNames.generate(rng, content, style, sex);
            if (taken.add(name.fullName().nominative())) {
                return name;
            }
        }
        throw new InvariantViolationException(
                ErrorDetails.of("check", "person_name_unique", "value", style.value(), "attempts", NAME_ATTEMPTS));
    }

    /** Рівні сектори «{@code <prefix><n>}» для кожної кількості з балансу. */
    static List<Sector<Integer>> countSectors(String prefix, CountRange range) {
        List<Sector<Integer>> sectors = new ArrayList<>();
        for (int count = range.min(); count <= range.max(); count++) {
            sectors.add(new Sector<>(prefix + count, 1, count, QUALITY, OutcomeTier.PARTIAL, List.of()));
        }
        return sectors;
    }

    /** Сектор на кожен тип з ненульовою вагою для міток держави, у порядку {@link PersonKind}; id — ключ типу. */
    static List<Sector<PersonKind>> kindSectors(ContentPack content, Set<String> tags) {
        List<Sector<PersonKind>> sectors = new ArrayList<>();
        for (PersonKindDef kind : content.personKinds().values()) {
            int weight = kind.weightFor(tags);
            if (weight > 0) {
                sectors.add(
                        new Sector<>(kind.kind().key(), weight, kind.kind(), QUALITY, OutcomeTier.PARTIAL, List.of()));
            }
        }
        return sectors;
    }

    /** Рівні сектори рис, доступних типу, ще не обраних і сумісних з обраними, за id; id сектора — id риси. */
    static List<Sector<TraitId>> traitSectors(ContentPack content, PersonKind kind, List<TraitId> chosen) {
        List<Sector<TraitId>> sectors = new ArrayList<>();
        for (TraitDef trait : content.traitsFor(kind)) {
            if (chosen.stream().allMatch(other -> content.compatible(trait.id(), other))) {
                sectors.add(new Sector<>(trait.id().value(), 1, trait.id(), QUALITY, OutcomeTier.PARTIAL, List.of()));
            }
        }
        return sectors;
    }

    private static <T> WheelSpin<T> spin(ContentPack content, Rng rng, WheelKind kind, List<Sector<T>> sectors) {
        int strength = content.balance().wheel().strength(kind);
        return Wheel.spin(rng, kind, sectors, Advantage.NONE, strength, TURN, null);
    }
}
