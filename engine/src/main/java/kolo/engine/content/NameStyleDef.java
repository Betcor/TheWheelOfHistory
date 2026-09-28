package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.util.Fixed;

/**
 * Мовний стиль коренів назв (GD §4.9): набори складів, з яких корінь збирається як початок + (вставка) + кінцівка.
 *
 * @param name назва стилю українською, для енциклопедії й налаштувань
 * @param starts початки; закінчуються приголосною
 * @param middles вставки між початком і кінцівкою; від голосної до приголосної
 * @param middleChanceBp імовірність вставки, базисні пункти 0..10 000
 * @param finals кінцівки з парадигмами відмінювання
 */
public record NameStyleDef(
        NameStyleId id,
        String name,
        List<String> starts,
        List<String> middles,
        int middleChanceBp,
        List<NameFinalDef> finals) {

    public NameStyleDef {
        Objects.requireNonNull(id, "id");
        String field = "name_style." + id;
        Checks.notBlank(field + ".name", name);
        starts = parts(field + ".starts", starts, false, true);
        middles = parts(field + ".middles", middles, true, true);
        Checks.inRange(field + ".middle_chance_bp", middleChanceBp, 0, Fixed.BP_SCALE);
        if (middleChanceBp > 0 && middles.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field + ".middles"));
        }
        if (starts.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field + ".starts"));
        }
        finals = List.copyOf(finals);
        if (finals.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field + ".finals"));
        }
        TreeSet<String> seenFinals = new TreeSet<>();
        for (NameFinalDef nameFinal : finals) {
            // Та сама кінцівка з іншою парадигмою дала б корінь із двома наборами форм.
            Defs.unique(field + ".finals", seenFinals, nameFinal.text());
        }
    }

    /**
     * @param vowelStart частина має починатися з голосної
     * @param consonantEnd частина має закінчуватися не голосною
     */
    private static List<String> parts(String field, List<String> parts, boolean vowelStart, boolean consonantEnd) {
        TreeSet<String> seen = new TreeSet<>();
        for (String part : parts) {
            NameText.part(field, part);
            if ((vowelStart && !NameText.startsWithVowel(part)) || (consonantEnd && NameText.endsWithVowel(part))) {
                throw NameText.invalid(field, part);
            }
            Defs.unique(field, seen, part);
        }
        return List.copyOf(parts);
    }
}
