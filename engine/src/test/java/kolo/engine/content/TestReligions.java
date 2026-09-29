package kolo.engine.content;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.Stat;

/**
 * Мінімальний валідний шаблон релігій для тестових пакетів: два архетипи, три аспекти, три догмати (священна війна
 * несумісна з ненасильством), два устрої й по формі назви на архетип.
 */
public final class TestReligions {

    /** 2–3 аспекти, 1–2 догмати. */
    public static final ReligionBalanceDef BALANCE = new ReligionBalanceDef(new CountRange(2, 3), new CountRange(1, 2));

    private TestReligions() {}

    public static ReligionContent content() {
        return new ReligionContent(
                List.of(archetype("monotheism", "Єдиний Бог"), archetype("polytheism", "Верховне божество")),
                List.of(
                        aspect("war", Map.of("archetype_polytheism", 50)),
                        aspect("knowledge", Map.of()),
                        aspect("sea", Map.of("religion_war", -50))),
                List.of(
                        dogma("holy_war", Map.of("religion_war", 100), "pacifism"),
                        dogma("pacifism", Map.of()),
                        dogma("scholar_honor", Map.of("religion_knowledge", 100))),
                List.of(polity("single_church", Map.of("archetype_monotheism", 100)), polity("communities", Map.of())),
                List.of(
                        path(List.of(new ArchetypeId("monotheism"), new ArchetypeId("polytheism"))),
                        temple(List.of(new ArchetypeId("polytheism")))));
    }

    static ArchetypeDef archetype(String id, String figure) {
        return new ArchetypeDef(
                new ArchetypeId(id), "Архетип " + id, "Опис " + id, figure, 100, List.of("archetype_" + id));
    }

    static AspectDef aspect(String id, Map<String, Integer> weightTags) {
        return new AspectDef(
                new AspectId(id), "Аспект " + id, "Опис " + id, 100, sorted(weightTags), List.of("religion_" + id));
    }

    static DogmaDef dogma(String id, Map<String, Integer> weightTags, String... incompatible) {
        return new DogmaDef(
                new DogmaId(id),
                "Догмат " + id,
                "Опис " + id,
                100,
                sorted(weightTags),
                List.of(new ModifierDef(ModifierTarget.stat(Stat.STABILITY), 5)),
                List.of("dogma_" + id),
                List.of(incompatible).stream().map(DogmaId::new).toList());
    }

    static ReligionPolityDef polity(String id, Map<String, Integer> weightTags) {
        return new ReligionPolityDef(
                new ReligionPolityId(id),
                "Устрій " + id,
                "Опис " + id,
                100,
                sorted(weightTags),
                List.of(),
                List.of("polity_" + id));
    }

    /** «Шлях {figure}», ім'я в родовому: «Шляхом Оріна». */
    static FaithFormDef path(List<ArchetypeId> archetypes) {
        return new FaithFormDef(
                new FaithFormId("path"),
                GrammaticalGender.MASCULINE,
                List.of(
                        "Шлях {figure}",
                        "Шляху {figure}",
                        "Шляху {figure}",
                        "Шлях {figure}",
                        "Шляхом {figure}",
                        "Шляху {figure}",
                        "Шляху {figure}"),
                GrammaticalCase.GENITIVE,
                archetypes);
    }

    /** «Храм {figure}», ім'я в родовому. */
    static FaithFormDef temple(List<ArchetypeId> archetypes) {
        return new FaithFormDef(
                new FaithFormId("temple"),
                GrammaticalGender.MASCULINE,
                List.of(
                        "Храм {figure}",
                        "Храму {figure}",
                        "Храму {figure}",
                        "Храм {figure}",
                        "Храмом {figure}",
                        "Храмі {figure}",
                        "Храме {figure}"),
                GrammaticalCase.GENITIVE,
                archetypes);
    }

    private static SortedMap<String, Integer> sorted(Map<String, Integer> map) {
        return new TreeMap<>(map);
    }
}
