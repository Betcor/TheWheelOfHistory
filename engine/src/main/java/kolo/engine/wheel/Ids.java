package kolo.engine.wheel;

/** Перевірка ключів контенту: {@code snake_case} англійською. */
final class Ids {

    private Ids() {}

    static String requireSnakeCase(String id, String what) {
        if (id == null || id.isEmpty() || !isLowerLetter(id.charAt(0))) {
            throw new IllegalArgumentException(what + " має бути snake_case: " + id);
        }
        for (int i = 1; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!isLowerLetter(c) && !(c >= '0' && c <= '9') && c != '_') {
                throw new IllegalArgumentException(what + " має бути snake_case: " + id);
            }
        }
        return id;
    }

    private static boolean isLowerLetter(char c) {
        return c >= 'a' && c <= 'z';
    }
}
