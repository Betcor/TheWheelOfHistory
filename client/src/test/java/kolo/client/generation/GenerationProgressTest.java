package kolo.client.generation;

import static kolo.client.generation.TestRolls.roll;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationProgressTest {

    private static final GenerationPlan PLAN = GenerationPlan.of(List.of(
            roll("generation_area"),
            roll("generation_population"),
            roll("generation_ideology"),
            roll("generation_name")));

    @Test
    void stepsThenStagesThenTheCard() {
        GenerationProgress progress = new GenerationProgress(PLAN);

        assertThat(progress.stage().kind()).isEqualTo(GenerationStage.LAND);
        assertThat(progress.nextStep()).isPresent();
        assertThat(progress.stageDone()).isFalse();
        assertThat(progress.nextStep()).isPresent();
        assertThat(progress.nextStep()).isEmpty();
        assertThat(progress.stageDone()).isTrue();

        progress.nextStage();
        assertThat(progress.stageIndex()).isEqualTo(1);
        assertThat(progress.stage().kind()).isEqualTo(GenerationStage.REGIME);
        progress.nextStage();
        assertThat(progress.stage().kind()).isEqualTo(GenerationStage.NAME);
        progress.nextStage();
        assertThat(progress.onCard()).isTrue();
        assertThat(progress.nextStep()).isEmpty();
        progress.nextStage();
        assertThat(progress.onCard()).isTrue();
    }

    @Test
    void skipReturnsTheRestOfTheStage() {
        GenerationProgress progress = new GenerationProgress(PLAN);
        progress.nextStep();

        assertThat(progress.skipStage())
                .singleElement()
                .satisfies(step -> assertThat(step.rolls())
                        .extracting(r -> r.kind().id())
                        .containsExactly("generation_population"));
        assertThat(progress.stageDone()).isTrue();
        assertThat(progress.skipStage()).isEmpty();
    }

    @Test
    void showAllAndRestart() {
        GenerationProgress progress = new GenerationProgress(PLAN);
        progress.nextStep();

        progress.showAll();
        assertThat(progress.onCard()).isTrue();
        progress.restart();
        assertThat(progress.onCard()).isFalse();
        assertThat(progress.stageIndex()).isZero();
        assertThat(progress.nextStep()).isPresent();
    }

    @Test
    void withoutRollsItIsTheCardAtOnce() {
        assertThat(new GenerationProgress(GenerationPlan.of(List.of())).onCard())
                .isTrue();
    }
}
