package kolo.engine.content;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Перевірки частин назв. Частини пишуться малими українськими літерами; генератор сам робить першу літеру великою.
 *
 * <p>Стик частин будується так, щоб назва читалася: початок закінчується приголосною, вставка — від голосної до
 * приголосної, кінцівка починається з голосної. Тоді будь-яке поєднання дає чергування приголосних і голосних на
 * межах («вел» + «ім» + «ор» → «Велімор»).
 */
final class NameText {

    private static final String LETTERS = "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя'";
    private static final String VOWELS = "аеєиіїоуюя";

    private NameText() {}

    /** Частина назви: непорожня, лише малі українські літери й апостроф, не починається з «ь» чи апострофа. */
    static String part(String field, String value) {
        if (value == null
                || value.isEmpty()
                || !letters(value)
                || value.charAt(0) == 'ь'
                || value.charAt(0) == '\''
                || value.charAt(value.length() - 1) == '\'') {
            throw invalid(field, value);
        }
        return value;
    }

    /** Закінчення відмінка: може бути порожнім («Велор» у називному), інакше — лише малі українські літери. */
    static String ending(String field, String value) {
        if (value == null) {
            throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", field));
        }
        if (!letters(value)) {
            throw invalid(field, value);
        }
        return value;
    }

    /**
     * Початки чи вставки: кожна частина закінчується приголосною й не повторюється; порядок зберігається.
     *
     * @param vowelStart частина має починатися з голосної (вставка)
     */
    static List<String> parts(String field, List<String> parts, boolean vowelStart) {
        TreeSet<String> seen = new TreeSet<>();
        for (String part : parts) {
            part(field, part);
            if ((vowelStart && !startsWithVowel(part)) || endsWithVowel(part)) {
                throw invalid(field, part);
            }
            Defs.unique(field, seen, part);
        }
        return List.copyOf(parts);
    }

    /** Кінцівка: частина назви, що починається з голосної. */
    static String finalText(String field, String value) {
        part(field, value);
        if (!startsWithVowel(value)) {
            throw invalid(field, value);
        }
        return value;
    }

    static boolean startsWithVowel(String part) {
        return isVowel(part.charAt(0));
    }

    static boolean endsWithVowel(String part) {
        return isVowel(part.charAt(part.length() - 1));
    }

    static ValidationException invalid(String field, String value) {
        return new ValidationException(
                ErrorCode.INVALID_NAME_FORMAT, ErrorDetails.of("field", field, "value", String.valueOf(value)));
    }

    private static boolean letters(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (LETTERS.indexOf(value.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isVowel(char c) {
        return VOWELS.indexOf(c) >= 0;
    }
}
