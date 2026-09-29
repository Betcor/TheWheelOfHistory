package kolo.engine.generation.religion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.ArchetypeDef;
import kolo.engine.content.AspectDef;
import kolo.engine.content.AspectId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.DogmaDef;
import kolo.engine.content.DogmaId;
import kolo.engine.content.FaithFormDef;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.ReligionContent;
import kolo.engine.content.ReligionPolityDef;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.generation.name.PersonNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Sex;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Ланцюжок коліс однієї релігії світу (GD §25.1): архетип → аспекти → догмати → устрій → назва.
 *
 * <ol>
 *   <li>Колесо {@link #ARCHETYPE_KIND}: сектор на кожен архетип з його вагою, у порядку контенту.
 *   <li>Колесо {@link #ASPECT_COUNT_KIND} з рівними секторами обирає кількість аспектів ({@code religion.aspects}),
 *       колесо {@link #ASPECT_KIND} — кожен аспект серед ще не обраних з вагою {@link AspectDef#weightFor} > 0 за
 *       мітками архетипу й уже обраних аспектів.
 *   <li>Так само {@link #DOGMA_COUNT_KIND} і {@link #DOGMA_KIND}: догмати ще й сумісні з уже обраними, ваги — за
 *       мітками архетипу, аспектів і догматів. Якщо аспектів чи догматів забракло, їх менше.
 *   <li>Колесо {@link #POLITY_KIND}: устрої з вагою > 0 за всіма мітками релігії.
 *   <li>Колесо {@link #FAITH_FORM_KIND} з рівними секторами обирає форму назви серед доступних архетипу. Стать
 *       постаті — рівноймовірно серед дозволених архетипом, мовний стиль імені — рівноймовірно серед стилів імен
 *       людей; це не колеса: вони не впливають на гру. Якщо повна назва віри вже зайнята, стиль та ім'я постаті
 *       перегенеруються.
 * </ol>
 *
 * <p>Частини не бувають кращими чи гіршими: сектори мають рівень {@link OutcomeTier#PARTIAL}, нейтральну якість
 * {@value #QUALITY}, перевага не діє — архетип і обрані частини впливають лише вагами.
 */
public final class ReligionWheel {

    public static final WheelKind ARCHETYPE_KIND = new WheelKind("generation_religion_archetype");
    public static final WheelKind ASPECT_COUNT_KIND = new WheelKind("generation_religion_aspect_count");
    public static final WheelKind ASPECT_KIND = new WheelKind("generation_religion_aspect");
    public static final WheelKind DOGMA_COUNT_KIND = new WheelKind("generation_religion_dogma_count");
    public static final WheelKind DOGMA_KIND = new WheelKind("generation_religion_dogma");
    public static final WheelKind POLITY_KIND = new WheelKind("generation_religion_polity");
    public static final WheelKind FAITH_FORM_KIND = new WheelKind("generation_religion_faith_form");

    /** Релігії генеруються для світу, а не держави: в стріках генерації вони не рахуються. */
    static final int QUALITY = 50;

    /**
     * Скільки разів перегенерувати ім'я постаті, з яким назва віри вже зайнята. Кожен стиль вбудованого контенту дає
     * тисячі імен, тож вичерпати спроби — ознака бракованого контенту чи бага, а не невдачі.
     */
    static final int NAME_ATTEMPTS = 100;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private ReligionWheel() {}

    /**
     * @param rng окремий потік релігії; всередині розгалужується на архетип, аспекти, догмати, устрій, форму назви,
     *     стать, стиль і ім'я постаті, тож зміна одного кидка не зсуває інших
     * @param takenNames повні назви вір у називному відмінку, вже зайняті в світі; нова їх не повторює
     * @throws InvariantViolationException якщо для міток релігії немає жодного устрою з вагою > 0 або за
     *     {@value #NAME_ATTEMPTS} спроб не вдалося скласти вільну назву
     */
    public static StartReligion generate(Rng rng, ContentPack content, Set<String> takenNames) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(takenNames, "takenNames");
        ReligionContent religions = content.religions();
        List<RollRecord> rolls = new ArrayList<>();

        WheelSpin<ArchetypeDef> archetypeSpin =
                spin(content, rng.fork("archetype"), ARCHETYPE_KIND, archetypeSectors(religions));
        rolls.add(archetypeSpin.record());
        ArchetypeDef archetype = archetypeSpin.value();
        TreeSet<String> tags = new TreeSet<>(archetype.tags());

        Rng aspectRng = rng.fork("aspects");
        WheelSpin<Integer> aspectCount = spin(
                content,
                aspectRng,
                ASPECT_COUNT_KIND,
                countSectors("aspects_", content.balance().religion().aspects()));
        rolls.add(aspectCount.record());
        List<AspectId> aspects = new ArrayList<>();
        for (int i = 0; i < aspectCount.value(); i++) {
            List<Sector<AspectDef>> sectors = aspectSectors(religions, tags, aspects);
            if (sectors.isEmpty()) {
                break;
            }
            WheelSpin<AspectDef> spin = spin(content, aspectRng, ASPECT_KIND, sectors);
            rolls.add(spin.record());
            aspects.add(spin.value().id());
            // Мітки обраного аспекту одразу діють на наступні аспекти.
            tags.addAll(spin.value().tags());
        }

        Rng dogmaRng = rng.fork("dogmas");
        WheelSpin<Integer> dogmaCount = spin(
                content,
                dogmaRng,
                DOGMA_COUNT_KIND,
                countSectors("dogmas_", content.balance().religion().dogmas()));
        rolls.add(dogmaCount.record());
        List<DogmaId> dogmas = new ArrayList<>();
        for (int i = 0; i < dogmaCount.value(); i++) {
            List<Sector<DogmaDef>> sectors = dogmaSectors(religions, tags, dogmas);
            if (sectors.isEmpty()) {
                break;
            }
            WheelSpin<DogmaDef> spin = spin(content, dogmaRng, DOGMA_KIND, sectors);
            rolls.add(spin.record());
            dogmas.add(spin.value().id());
            tags.addAll(spin.value().tags());
        }

        List<Sector<ReligionPolityDef>> polities = politySectors(religions, tags);
        if (polities.isEmpty()) {
            // Вбудований контент це виключає (інтеграційний тест): кожній релігії доступний хоча б один устрій.
            throw new InvariantViolationException(ErrorDetails.of(
                    "check",
                    "religion_polity_available",
                    "value",
                    archetype.id().value()));
        }
        WheelSpin<ReligionPolityDef> politySpin = spin(content, rng.fork("polity"), POLITY_KIND, polities);
        rolls.add(politySpin.record());
        tags.addAll(politySpin.value().tags());

        WheelSpin<FaithFormDef> formSpin =
                spin(content, rng.fork("faith_form"), FAITH_FORM_KIND, faithFormSectors(religions, archetype));
        rolls.add(formSpin.record());
        FaithFormDef form = formSpin.value();

        List<Sex> sexes = archetype.figureSexes();
        Sex sex = sexes.get(rng.fork("figure_sex").nextInt(sexes.size()));
        List<NameStyleId> styles = List.copyOf(content.names().personStyles().keySet());
        Rng styleRng = rng.fork("figure_style");
        Rng nameRng = rng.fork("figure_name");
        for (int attempt = 0; attempt < NAME_ATTEMPTS; attempt++) {
            // Стиль перекидається разом з іменем: у вузькому стилі імен на стать може бути лише кілька.
            NameStyleId style = styles.get(styleRng.nextInt(styles.size()));
            NounPhrase figure = PersonNames.given(nameRng, content, style, sex);
            NounPhrase name = name(form, figure);
            if (!takenNames.contains(name.nominative())) {
                return new StartReligion(
                        archetype.id(),
                        aspects,
                        dogmas,
                        politySpin.value().id(),
                        form.id(),
                        sex,
                        figure,
                        name,
                        tags,
                        rolls);
            }
        }
        throw new InvariantViolationException(
                ErrorDetails.of("check", "faith_name_unique", "value", form.id().value(), "attempts", NAME_ATTEMPTS));
    }

    /** Назва віри за формою: ім'я постаті стоїть у відмінку форми, відмінюється головне слово. */
    static NounPhrase name(FaithFormDef form, NounPhrase figure) {
        String figureForm = figure.form(form.figureCase());
        List<String> forms = new ArrayList<>(GrammaticalCase.values().length);
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            forms.add(form.render(grammaticalCase, figureForm));
        }
        return new NounPhrase(form.gender(), forms);
    }

    /** Сектор на кожен архетип з його вагою, у порядку контенту; id — id архетипу. */
    static List<Sector<ArchetypeDef>> archetypeSectors(ReligionContent religions) {
        List<Sector<ArchetypeDef>> sectors = new ArrayList<>();
        for (ArchetypeDef archetype : religions.archetypes()) {
            sectors.add(sector(archetype.id().value(), archetype.weight(), archetype));
        }
        return sectors;
    }

    /** Аспекти, ще не обрані, з вагою > 0 за мітками релігії, у порядку контенту; id — id аспекту. */
    static List<Sector<AspectDef>> aspectSectors(ReligionContent religions, Set<String> tags, List<AspectId> chosen) {
        List<Sector<AspectDef>> sectors = new ArrayList<>();
        for (AspectDef aspect : religions.aspects()) {
            int weight = aspect.weightFor(tags);
            if (weight > 0 && !chosen.contains(aspect.id())) {
                sectors.add(sector(aspect.id().value(), weight, aspect));
            }
        }
        return sectors;
    }

    /** Догмати, ще не обрані й сумісні з обраними, з вагою > 0 за мітками релігії; id — id догмату. */
    static List<Sector<DogmaDef>> dogmaSectors(ReligionContent religions, Set<String> tags, List<DogmaId> chosen) {
        List<Sector<DogmaDef>> sectors = new ArrayList<>();
        for (DogmaDef dogma : religions.dogmas()) {
            int weight = dogma.weightFor(tags);
            // compatible() хибне й для самого догмату, тож обрані вибувають разом із несумісними.
            if (weight > 0 && chosen.stream().allMatch(other -> religions.compatible(dogma.id(), other))) {
                sectors.add(sector(dogma.id().value(), weight, dogma));
            }
        }
        return sectors;
    }

    /** Устрої з вагою > 0 за всіма мітками релігії, у порядку контенту; id — id устрою. */
    static List<Sector<ReligionPolityDef>> politySectors(ReligionContent religions, Set<String> tags) {
        List<Sector<ReligionPolityDef>> sectors = new ArrayList<>();
        for (ReligionPolityDef polity : religions.polities()) {
            int weight = polity.weightFor(tags);
            if (weight > 0) {
                sectors.add(sector(polity.id().value(), weight, polity));
            }
        }
        return sectors;
    }

    /** Рівні сектори форм назви, доступних архетипу, у порядку контенту; id — id форми. */
    static List<Sector<FaithFormDef>> faithFormSectors(ReligionContent religions, ArchetypeDef archetype) {
        return religions.faithFormsFor(archetype.id()).stream()
                .map(form -> sector(form.id().value(), 1, form))
                .toList();
    }

    /** Рівні сектори «{@code <prefix><n>}» для кожної кількості з балансу. */
    static List<Sector<Integer>> countSectors(String prefix, CountRange range) {
        List<Sector<Integer>> sectors = new ArrayList<>();
        for (int count = range.min(); count <= range.max(); count++) {
            sectors.add(sector(prefix + count, 1, count));
        }
        return sectors;
    }

    private static <T> Sector<T> sector(String id, int weight, T value) {
        return new Sector<>(id, weight, value, QUALITY, OutcomeTier.PARTIAL, List.of());
    }

    static <T> WheelSpin<T> spin(ContentPack content, Rng rng, WheelKind kind, List<Sector<T>> sectors) {
        int strength = content.balance().wheel().strength(kind);
        return Wheel.spin(rng, kind, sectors, Advantage.NONE, strength, TURN, null);
    }
}
