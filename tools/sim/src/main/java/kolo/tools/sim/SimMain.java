package kolo.tools.sim;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kolo.engine.error.GameException;

/**
 * CLI headless-інструментів: {@code country --seed N} — згенерувати державу за seed.
 *
 * <p>Коди виходу: {@value #OK} — успіх, {@value #GAME_ERROR} — помилка гри (контент невалідний, порушено
 * інваріант), {@value #USAGE_ERROR} — неправильний виклик.
 */
public final class SimMain {

    static final int OK = 0;
    static final int GAME_ERROR = 1;
    static final int USAGE_ERROR = 2;

    private SimMain() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Виконує команду; ловить помилки тут, на межі інструмента, і перетворює на код виходу. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0) {
                throw new UsageException("error.usage.missing_command");
            }
            List<String> rest = Arrays.asList(args).subList(1, args.length);
            switch (args[0]) {
                case "country" -> {
                    CountryOptions options = CountryOptions.parse(rest);
                    CountryCommand.render(CountryCommand.run(options), options).forEach(out::println);
                }
                case "--help", "help" -> out.println(SimText.text("usage"));
                default -> throw new UsageException("error.usage.unknown_command", args[0]);
            }
            return OK;
        } catch (UsageException e) {
            err.println(e.text());
            err.println(SimText.text("usage"));
            return USAGE_ERROR;
        } catch (GameException e) {
            // Тексти кодів помилок — у клієнті; розробникові досить коду й подробиць.
            err.println(SimText.text("error.game", e.code().key(), details(e.details())));
            return GAME_ERROR;
        }
    }

    private static String details(Map<String, Object> details) {
        return details.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}
