package kolo.tools.sim;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Параметри команди {@code country}.
 *
 * @param seed seed світу: з нього — релігії світу й сама держава
 * @param countries скільки держав у світі; від цього залежить кількість релігій (таблиця {@code religion.count} у
 *     {@code balance.yaml})
 * @param resources id ресурсів держави (доки немає карти, їх задають вручну); перевіряються за контентом
 * @param rolls чи друкувати всі обертання коліс
 * @param content каталог з YAML-файлами контенту; порожньо — вбудований контент
 */
record CountryOptions(long seed, int countries, SortedSet<String> resources, boolean rolls, Optional<Path> content) {

    /** Типовий розмір світу — середина таблиці кількості релігій. */
    static final int DEFAULT_COUNTRIES = 20;

    /** Верхня межа лише від помилок набору; справжній розмір світу обмежить карта. */
    static final int MAX_COUNTRIES = 1000;

    CountryOptions {
        resources = Collections.unmodifiableSortedSet(new TreeSet<>(resources));
        Objects.requireNonNull(content, "content");
    }

    /**
     * @param args параметри після назви команди
     * @throws UsageException якщо параметр невідомий, повторюється, бракує значення, значення не число чи поза
     *     межами, або не задано {@code --seed}
     */
    static CountryOptions parse(List<String> args) {
        Long seed = null;
        Integer countries = null;
        TreeSet<String> resources = null;
        boolean rolls = false;
        Path content = null;
        for (int i = 0; i < args.size(); i++) {
            String option = args.get(i);
            switch (option) {
                case "--seed" -> {
                    once(option, seed);
                    seed = parseLong(option, value(args, ++i, option));
                }
                case "--countries" -> {
                    once(option, countries);
                    countries = parseInt(option, value(args, ++i, option), 1, MAX_COUNTRIES);
                }
                case "--resources" -> {
                    once(option, resources);
                    resources = new TreeSet<>();
                    for (String id : value(args, ++i, option).split(",", -1)) {
                        resources.add(id.strip());
                    }
                }
                case "--rolls" -> {
                    if (rolls) {
                        throw new UsageException("error.usage.duplicate_option", option);
                    }
                    rolls = true;
                }
                case "--content" -> {
                    once(option, content);
                    content = Path.of(value(args, ++i, option));
                }
                default -> throw new UsageException("error.usage.unknown_option", option);
            }
        }
        if (seed == null) {
            throw new UsageException("error.usage.missing_seed");
        }
        return new CountryOptions(
                seed,
                countries == null ? DEFAULT_COUNTRIES : countries,
                resources == null ? new TreeSet<>() : resources,
                rolls,
                Optional.ofNullable(content));
    }

    private static void once(String option, Object current) {
        if (current != null) {
            throw new UsageException("error.usage.duplicate_option", option);
        }
    }

    private static String value(List<String> args, int index, String option) {
        if (index >= args.size() || args.get(index).startsWith("--")) {
            throw new UsageException("error.usage.missing_value", option);
        }
        return args.get(index);
    }

    private static long parseLong(String option, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new UsageException("error.usage.not_a_number", option, value);
        }
    }

    private static int parseInt(String option, String value, int min, int max) {
        long parsed = parseLong(option, value);
        if (parsed < min || parsed > max) {
            throw new UsageException("error.usage.out_of_range", option, value, min, max);
        }
        return (int) parsed;
    }
}
