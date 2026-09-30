package kolo.engine.content;

import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Числа колеса ресурсів держави (GD §4.5).
 *
 * @param count таблиця кількості обертань за кількістю провінцій держави; рядки — за строго зростаючим {@code
 *     max_provinces}
 */
public record ResourceBalanceDef(List<ResourceCountDef> count) {

    /**
     * @throws ValidationException якщо таблиця порожня ({@link ErrorCode#EMPTY_COLLECTION}) або її рядки не зростають
     *     за кількістю провінцій ({@link ErrorCode#OUT_OF_ORDER})
     */
    public ResourceBalanceDef {
        count = List.copyOf(count);
        if (count.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "resources.count"));
        }
        for (int i = 1; i < count.size(); i++) {
            int maxProvinces = count.get(i).maxProvinces();
            if (maxProvinces <= count.get(i - 1).maxProvinces()) {
                throw new ValidationException(
                        ErrorCode.OUT_OF_ORDER,
                        ErrorDetails.of("field", "resources.count[" + i + "].max_provinces", "value", maxProvinces));
            }
        }
    }

    /**
     * Скільки обертань у держави з такою кількістю провінцій: перший рядок, що її вміщує; більше, ніж в останньому
     * рядку, — як в останньому.
     *
     * @param provinces провінцій держави, {@code ≥ 1}
     */
    public CountRange deposits(int provinces) {
        Checks.inRange("provinces", provinces, 1, Integer.MAX_VALUE);
        for (ResourceCountDef row : count) {
            if (provinces <= row.maxProvinces()) {
                return row.deposits();
            }
        }
        return count.getLast().deposits();
    }
}
