package kolo.engine.turn;

import java.util.Objects;
import kolo.engine.content.ContentPack;
import kolo.engine.state.WorldInvariants;
import kolo.engine.state.WorldState;

/**
 * Конвеєр року: чернетка (глибока копія стану) → фази → наступний рік → перевірка інваріантів. Однаковий вхід —
 * однаковий вихід.
 *
 * <p>Поки фаз немає: рік «порожній», змінюється лише номер року. Накази й фази з'являться зі своїми системами, тоді ж
 * — наказ і події {@code ORDER_FAILED}.
 */
public final class TurnPipeline {

    private TurnPipeline() {}

    /**
     * Розв'язує рік.
     *
     * @param state стан на початку року; не змінюється
     * @param content контент, з яким створено світ
     * @throws kolo.engine.error.InvariantViolationException якщо стан після року неконсистентний — баг рушія
     */
    public static TurnResult resolve(WorldState state, ContentPack content) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(content, "content");
        WorldState draft = state.deepCopy();
        draft.setTurn(state.turn() + 1);
        WorldInvariants.check(draft);
        return new TurnResult(draft);
    }
}
