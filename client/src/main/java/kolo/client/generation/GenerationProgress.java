package kolo.client.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Де гравець у показі генерації: етап, наступний крок етапу, картка держави наприкінці. Без JavaFX — екран лише
 * виконує кроки й показує, що звідси випливає.
 */
public final class GenerationProgress {

    private final GenerationPlan plan;
    private int stage;
    private int step;
    private boolean card;

    public GenerationProgress(GenerationPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.card = plan.stages().isEmpty();
    }

    public GenerationPlan plan() {
        return plan;
    }

    /** Чи показ дійшов до картки держави. */
    public boolean onCard() {
        return card;
    }

    /** Номер поточного етапу від 0. */
    public int stageIndex() {
        return stage;
    }

    /** Поточний етап; на картці — останній. */
    public GenerationPlan.Stage stage() {
        return plan.stages().get(stage);
    }

    /** Наступний крок поточного етапу; порожньо — етап показано весь (або вже картка). */
    public Optional<GenerationPlan.Step> nextStep() {
        if (card || stageDone()) {
            return Optional.empty();
        }
        return Optional.of(stage().steps().get(step++));
    }

    public boolean stageDone() {
        return card || step >= stage().steps().size();
    }

    /** «Пропустити анімацію»: решта кроків етапу — одразу; повертає їх, щоб показати результат. */
    public List<GenerationPlan.Step> skipStage() {
        List<GenerationPlan.Step> rest = new ArrayList<>();
        Optional<GenerationPlan.Step> next;
        while ((next = nextStep()).isPresent()) {
            rest.add(next.get());
        }
        return rest;
    }

    /** До наступного етапу; після останнього — картка. Етап має бути показаний весь. */
    public void nextStage() {
        if (card) {
            return;
        }
        if (stage + 1 < plan.stages().size()) {
            stage++;
            step = 0;
        } else {
            card = true;
        }
    }

    /** «Показати все одразу»: картка держави. */
    public void showAll() {
        card = true;
    }

    /** Показати ще раз з першого етапу. */
    public void restart() {
        stage = 0;
        step = 0;
        card = plan.stages().isEmpty();
    }
}
