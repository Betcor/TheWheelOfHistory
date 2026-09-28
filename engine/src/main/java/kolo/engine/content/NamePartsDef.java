package kolo.engine.content;

import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.util.Fixed;

/**
 * Склади, з яких збирається основа імені чи прізвища: початок + (вставка). Кінцівку з парадигмою додає власник.
 *
 * @param starts початки; закінчуються приголосною
 * @param middles вставки між початком і кінцівкою; від голосної до приголосної
 * @param middleChanceBp імовірність вставки, базисні пункти 0..10 000
 */
public record NamePartsDef(List<String> starts, List<String> middles, int middleChanceBp) {

    /** @throws ValidationException якщо початків немає, частина не того формату чи повторюється */
    public NamePartsDef {
        starts = NameText.parts("name_parts.starts", starts, false);
        middles = NameText.parts("name_parts.middles", middles, true);
        Checks.inRange("name_parts.middle_chance_bp", middleChanceBp, 0, Fixed.BP_SCALE);
        if (starts.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "name_parts.starts"));
        }
        if (middleChanceBp > 0 && middles.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "name_parts.middles"));
        }
    }
}
