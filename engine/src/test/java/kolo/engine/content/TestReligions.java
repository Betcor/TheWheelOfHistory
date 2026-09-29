package kolo.engine.content;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.Sex;
import kolo.engine.state.Stat;

/**
 * Мінімальний валідний шаблон релігій для тестових пакетів: два архетипи (постать монотеїзму — лише чоловік,
 * політеїзму — будь-хто), три аспекти, три догмати (священна війна несумісна з ненасильством), два устрої, форма
 * «Шлях» для обох архетипів і «Храм» лише для політеїзму.
 */
public final class TestReligions {

    /** До 8 держав — 2–3 релігії. */
    public static final ReligionCountDef FEW_COUNTRIES = new ReligionCountDef(8, new CountRange(2, 3));

    /** До 20 держав (і більше) — 4–5 релігій. */
    public static final ReligionCountDef MANY_COUNTRIES = new ReligionCountDef(20, new CountRange(4, 5));

    /** 2–3 аспекти, 1–2 догмати. */
    public static final ReligionBalanceDef BALANCE =
            new ReligionBalanceDef(List.of(FEW_COUNTRIES, MANY_COUNTRIES), new CountRange(2, 3), new CountRange(1, 2));

    private TestReligions() {}

    public static ReligionContent content() {
        return new ReligionContent(
                List.of(
                        archetype("monotheism", "Єдиний Бог", Sex.MALE),
                        archetype("polytheism", "Верховне божество", Sex.MALE, Sex.FEMALE)),
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

    public static ArchetypeDef archetype(String id, String figure, Sex... sexes) {
        return new ArchetypeDef(
                new ArchetypeId(id),
                "Архетип " + id,
                "Опис " + id,
                figure,
                List.of(sexes),
                100,
                List.of("archetype_" + id));
    }

    public static AspectDef aspect(String id, Map<String, Integer> weightTags) {
        return new AspectDef(
                new AspectId(id), "Аспект " + id, "Опис " + id, 100, sorted(weightTags), List.of("religion_" + id));
    }

    public static DogmaDef dogma(String id, Map<String, Integer> weightTags, String... incompatible) {
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

    public static ReligionPolityDef polity(String id, Map<String, Integer> weightTags) {
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
    public static FaithFormDef path(List<ArchetypeId> archetypes) {
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
    public static FaithFormDef temple(List<ArchetypeId> archetypes) {
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
