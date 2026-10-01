package kolo.client.generation;

import static kolo.client.generation.TestRolls.roll;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

class GenerationPlanTest {

    private static final List<RollRecord> CHAIN = List.of(
            roll("generation_continent"),
            roll("generation_area"),
            roll("generation_population"),
            roll("generation_ideology"),
            roll("generation_sub_ideology"),
            roll("generation_state_religion"),
            roll("generation_development_economy"),
            roll("generation_development_military"),
            roll("generation_development_society"),
            roll("generation_development_energy_science"),
            roll("generation_gdp"),
            // Стрік спрацював після ВВП — його колесо одразу слідом.
            roll("generation_golden_age"),
            roll("generation_hdi"),
            roll("generation_army_size"),
            roll("generation_army_training"),
            roll("generation_resource_count"),
            roll("generation_resource"),
            roll("generation_resource"),
            roll("generation_nuclear"),
            roll("generation_warheads"),
            roll("generation_backstory_count"),
            roll("generation_backstory"),
            roll("generation_name"),
            roll("generation_people_count"),
            roll("generation_person_kind"),
            roll("generation_person_trait_count"),
            roll("generation_person_trait"));

    @Test
    void rollsAreSplitIntoTheEightStagesInOrder() {
        GenerationPlan plan = GenerationPlan.of(CHAIN);

        assertThat(plan.stages()).extracting(GenerationPlan.Stage::kind).containsExactly(GenerationStage.values());
        assertThat(plan.stages().stream().flatMap(stage -> stage.rolls().stream()))
                .containsExactlyElementsOf(CHAIN);
    }

    @Test
    void independentWheelsSpinTogether() {
        GenerationPlan plan = GenerationPlan.of(CHAIN);

        GenerationPlan.Stage development = plan.stages().get(2);
        assertThat(development.steps()).extracting(step -> step.rolls().size()).containsExactly(4, 1, 1, 1);
        assertThat(development.steps().get(2).rolls().getFirst().kind().id()).isEqualTo("generation_golden_age");
        GenerationPlan.Stage army = plan.stages().get(3);
        assertThat(army.steps())
                .singleElement()
                .satisfies(step -> assertThat(step.rolls()).hasSize(2));
        // Колеса, що залежать одне від одного, — по черзі.
        assertThat(plan.stages().getFirst().steps()).hasSize(3);
        assertThat(plan.stages().get(4).steps()).hasSize(5);
    }

    @Test
    void sameKindTwiceIsNotTogether() {
        GenerationPlan plan = GenerationPlan.of(List.of(roll("generation_army_size"), roll("generation_army_size")));

        assertThat(plan.stages().getFirst().steps()).hasSize(2);
    }

    @Test
    void unknownWheelGoesWithThePreviousOne() {
        GenerationPlan plan = GenerationPlan.of(
                List.of(roll("generation_name"), roll("generation_mystery"), roll("generation_people_count")));

        assertThat(plan.stages())
                .extracting(GenerationPlan.Stage::kind)
                .containsExactly(GenerationStage.NAME, GenerationStage.PEOPLE);
        assertThat(plan.stages().getFirst().rolls()).hasSize(2);
        assertThat(GenerationPlan.of(List.of(roll("generation_mystery"))).stages())
                .extracting(GenerationPlan.Stage::kind)
                .containsExactly(GenerationStage.LAND);
    }

    @Test
    void emptyRollsGiveNoStages() {
        assertThat(GenerationPlan.of(List.of()).stages()).isEmpty();
    }

    @Test
    void keyWheelsAreTheOnesOfTheDesign() {
        // GD §4.12: площа, населення, ідеологія, релігія, 4 галузі, ВВП, ІЛР, армія, вишкіл, ядерний статус, назва,
        // стрік.
        List<String> key = CHAIN.stream()
                .map(RollRecord::kind)
                .filter(GenerationPlan::isKey)
                .map(WheelKind::id)
                .toList();

        assertThat(key)
                .containsExactly(
                        "generation_area",
                        "generation_population",
                        "generation_ideology",
                        "generation_state_religion",
                        "generation_development_economy",
                        "generation_development_military",
                        "generation_development_society",
                        "generation_development_energy_science",
                        "generation_gdp",
                        "generation_golden_age",
                        "generation_hdi",
                        "generation_army_size",
                        "generation_army_training",
                        "generation_nuclear",
                        "generation_name");
        assertThat(GenerationPlan.isKey(new WheelKind("generation_underdog"))).isTrue();
    }

    @Test
    void modesChooseWhatSpins() {
        WheelKind key = new WheelKind("generation_area");
        WheelKind service = new WheelKind("generation_resource");

        assertThat(AnimationMode.ALL.animates(service)).isTrue();
        assertThat(AnimationMode.KEY.animates(key)).isTrue();
        assertThat(AnimationMode.KEY.animates(service)).isFalse();
        assertThat(AnimationMode.NONE.animates(key)).isFalse();
    }
}
