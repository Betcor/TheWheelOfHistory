package kolo.client.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import kolo.engine.content.StreakKind;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.WheelKind;

/**
 * Записи коліс генерації держави, розкладені для показу (GD §4.12): етапи в порядку показу, у кожному — кроки, а крок —
 * колеса, що крутяться разом (незалежні одне від одного: 4 галузі розвиненості; розмір армії й вишкіл). Порядок коліс —
 * порядок обертання. Колесо, якого план не знає (новий тип у контенті), іде в етап попереднього колеса — так і колесо
 * стріку, що крутиться одразу після колеса, яке його викликало.
 *
 * @param stages етапи, у яких є хоч одне колесо, у порядку показу
 */
public record GenerationPlan(List<Stage> stages) {

    private static final String DEVELOPMENT = "generation_development_";

    /** Ключові колеса — з анімацією в режимі «важливі» (GD §4.12); колеса стріку — теж ({@link #isKey}). */
    private static final Set<String> KEY = Set.of(
            "generation_area",
            "generation_population",
            "generation_ideology",
            "generation_state_religion",
            "generation_gdp",
            "generation_hdi",
            "generation_army_size",
            "generation_army_training",
            "generation_nuclear",
            "generation_name");

    /** Колеса, що крутяться разом з такими самими сусідами в одному кроці. */
    private static final Set<String> ARMY = Set.of("generation_army_size", "generation_army_training");

    public GenerationPlan {
        stages = List.copyOf(stages);
    }

    /** Етап показу. */
    public record Stage(GenerationStage kind, List<Step> steps) {

        public Stage {
            Objects.requireNonNull(kind, "kind");
            steps = List.copyOf(steps);
        }

        /** Колеса етапу в порядку обертання. */
        public List<RollRecord> rolls() {
            return steps.stream().flatMap(step -> step.rolls().stream()).toList();
        }
    }

    /** Колеса, що крутяться одночасно. */
    public record Step(List<RollRecord> rolls) {

        public Step {
            rolls = List.copyOf(rolls);
        }
    }

    /** @param rolls записи коліс генерації в порядку обертання */
    public static GenerationPlan of(List<RollRecord> rolls) {
        List<Stage> stages = new ArrayList<>();
        GenerationStage current = GenerationStage.LAND;
        List<List<RollRecord>> steps = new ArrayList<>();
        RollRecord previous = null;
        for (RollRecord roll : rolls) {
            GenerationStage stage = stageOf(roll.kind()).orElse(current);
            if (stage != current) {
                close(stages, current, steps);
                current = stage;
                steps = new ArrayList<>();
                previous = null;
            }
            if (previous != null && together(previous.kind(), roll.kind())) {
                steps.getLast().add(roll);
            } else {
                steps.add(new ArrayList<>(List.of(roll)));
            }
            previous = roll;
        }
        close(stages, current, steps);
        return new GenerationPlan(stages);
    }

    /** Ключове колесо генерації (GD §4.12): площа, населення, лад, релігія, розвиток, армія, ядерний статус, назва, стрік. */
    public static boolean isKey(WheelKind kind) {
        String id = kind.id();
        return KEY.contains(id) || id.startsWith(DEVELOPMENT) || streak(kind);
    }

    /** Чи це колесо стріку (GD §4.10). */
    static boolean streak(WheelKind kind) {
        for (StreakKind streak : StreakKind.values()) {
            if (kind.id().equals("generation_" + streak.key())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<GenerationStage> stageOf(WheelKind kind) {
        String id = kind.id();
        GenerationStage stage = switch (id) {
            case "generation_continent", "generation_area", "generation_population" -> GenerationStage.LAND;
            case "generation_ideology", "generation_sub_ideology", "generation_state_religion" ->
                GenerationStage.REGIME;
            case "generation_gdp", "generation_hdi" -> GenerationStage.DEVELOPMENT;
            case "generation_army_size", "generation_army_training" -> GenerationStage.ARMY;
            case "generation_resource_count", "generation_resource", "generation_nuclear", "generation_warheads" ->
                GenerationStage.RESOURCES;
            case "generation_backstory_count", "generation_backstory" -> GenerationStage.HISTORY;
            case "generation_name" -> GenerationStage.NAME;
            case "generation_people_count",
                    "generation_person_kind",
                    "generation_person_trait_count",
                    "generation_person_trait" -> GenerationStage.PEOPLE;
            default -> id.startsWith(DEVELOPMENT) ? GenerationStage.DEVELOPMENT : null;
        };
        return Optional.ofNullable(stage);
    }

    private static boolean together(WheelKind a, WheelKind b) {
        boolean development = a.id().startsWith(DEVELOPMENT) && b.id().startsWith(DEVELOPMENT);
        boolean army = ARMY.contains(a.id()) && ARMY.contains(b.id()) && !a.equals(b);
        return development || army;
    }

    private static void close(List<Stage> stages, GenerationStage kind, List<List<RollRecord>> steps) {
        if (!steps.isEmpty()) {
            stages.add(new Stage(kind, steps.stream().map(Step::new).toList()));
        }
    }
}
