package kolo.tools.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Смок-тест: CLI запускається, як {@code ./gradlew :tools:sim:run --args="country --seed 42"}. */
class SimSmokeTest {

    @Test
    void countryCommandPrintsACard() {
        Run run = run("country", "--seed", "42", "--rolls");

        assertThat(run.code()).isEqualTo(SimMain.OK);
        assertThat(run.out()).contains("Seed 42", "Назва: ", "Лад: ", "Відомі люди:", "Обертання:");
        assertThat(run.err()).isEmpty();
    }

    @Test
    void helpPrintsUsage() {
        Run run = run("--help");

        assertThat(run.code()).isEqualTo(SimMain.OK);
        assertThat(run.out()).contains("country --seed");
    }

    @Test
    void wrongCallsExitWithUsage() {
        assertUsage(run());
        assertUsage(run("world"));
        assertUsage(run("country"));
        assertUsage(run("country", "--seed", "x"));
    }

    @Test
    void brokenContentExitsWithGameError(@TempDir Path empty) {
        Run run = run("country", "--seed", "1", "--content", empty.toString());

        assertThat(run.code()).isEqualTo(SimMain.GAME_ERROR);
        assertThat(run.err()).contains("error.content_file_missing");
        assertThat(run.out()).isEmpty();
    }

    private static void assertUsage(Run run) {
        assertThat(run.code()).isEqualTo(SimMain.USAGE_ERROR);
        assertThat(run.err()).contains("Використання:");
        assertThat(run.out()).isEmpty();
    }

    private static Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = SimMain.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Run(int code, String out, String err) {}
}
