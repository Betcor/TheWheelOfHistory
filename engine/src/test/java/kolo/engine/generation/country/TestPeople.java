package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.PersonKindDef;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;

/**
 * Контент для тестів колеса відомих людей: типи з вагами за мітками, риси з обмеженнями за типом і несумісністю,
 * стиль з 12 іменами на стать.
 */
final class TestPeople {

    static final NameStyleId STYLE = new NameStyleId("northern");

    /** Базова вага кожного типу; генерал і вчений мають ще добавки за мітки. */
    static final int WEIGHT = 10;

    /** Хунта: генерал 10 + 30. */
    static final int JUNTA_BONUS = 30;

    /** Пацифізм: генерал 10 − 10 = 0 (не випадає), вчений 10 + 10. */
    static final int PACIFIST_GENERAL = -10;

    static final int PACIFIST_SCIENTIST = 10;

    static final TraitId LOYAL = new TraitId("loyal");
    static final TraitId TREACHEROUS = new TraitId("treacherous");
    static final TraitId CHARISMATIC = new TraitId("charismatic");
    static final TraitId GENIUS = new TraitId("genius");
    static final TraitId TACTICIAN = new TraitId("tactician");

    static final List<TraitDef> TRAITS = List.of(
            trait(LOYAL, List.of(), List.of(TREACHEROUS)),
            trait(TREACHEROUS, List.of(), List.of()),
            trait(CHARISMATIC, List.of(), List.of()),
            trait(GENIUS, List.of(PersonKind.SCIENTIST), List.of()),
            trait(TACTICIAN, List.of(PersonKind.GENERAL, PersonKind.ADMIRAL), List.of()));

    static final CountRange PEOPLE = new CountRange(1, 3);
    static final CountRange TRAIT_COUNT = new CountRange(1, 3);
    static final CountRange AGE = new CountRange(25, 70);

    static final ContentPack PACK = pack(new TestBackstory.People(
            kinds(),
            TRAITS,
            PEOPLE,
            TRAIT_COUNT,
            AGE,
            List.of("вел", "тор", "сал", "мар"),
            List.of("торв", "гальм", "марк")));

    private TestPeople() {}

    /** Пакет з цими постатями; решта контенту — мінімальна. */
    static ContentPack pack(TestBackstory.People people) {
        return TestBackstory.pack(
                List.of(new IdeologyDef(
                        new IdeologyId("democracy"),
                        "Демократія",
                        100,
                        List.of(),
                        List.of("democratic"),
                        List.of(new SubIdeologyDef(
                                new SubIdeologyId("liberal_democracy"), "Ліберальна", 100, List.of(), List.of())))),
                TestBackstory.FRAGMENTS,
                TestBackstory.COUNT,
                TestDevelopment.LEVELS,
                List.of(new ResourceDef(new ResourceId("iron"), "Залізо", List.of())),
                Arrays.stream(NuclearStatus.values())
                        .map(status -> new NuclearStatusDef(status, "Статус", 100, 50, List.of()))
                        .toList(),
                new CountRange(2, 10),
                10,
                TestGdp.LEVELS,
                TestGdp.DEVELOPMENT_ADVANTAGE,
                TestHdi.LEVELS,
                TestHdi.GDP_ADVANTAGE,
                TestArmy.SIZES,
                TestArmy.GDP_ADVANTAGE,
                TestBackstory.trainingLevels(),
                10,
                10,
                people);
    }

    /** Той самий контент, але з іншими кількістю постатей і стилем імен. */
    static ContentPack pack(CountRange people, List<String> givenStarts, List<String> surnameStarts) {
        return pack(new TestBackstory.People(kinds(), TRAITS, people, TRAIT_COUNT, AGE, givenStarts, surnameStarts));
    }

    /** Кожен тип з базовою вагою; генерал і вчений залежать від міток {@code junta} і {@code pacifist}. */
    static List<PersonKindDef> kinds() {
        List<PersonKindDef> kinds = new ArrayList<>();
        for (PersonKind kind : PersonKind.values()) {
            Map<String, Integer> bonuses = switch (kind) {
                case GENERAL -> Map.of("junta", JUNTA_BONUS, "pacifist", PACIFIST_GENERAL);
                case SCIENTIST -> Map.of("pacifist", PACIFIST_SCIENTIST);
                default -> Map.of();
            };
            kinds.add(new PersonKindDef(kind, "Тип " + kind.key(), "Опис", WEIGHT, new TreeMap<>(bonuses), List.of()));
        }
        return List.copyOf(kinds);
    }

    private static TraitDef trait(TraitId id, List<PersonKind> kinds, List<TraitId> incompatible) {
        return new TraitDef(id, "Риса " + id, kinds, List.of(), List.of(), incompatible);
    }
}
