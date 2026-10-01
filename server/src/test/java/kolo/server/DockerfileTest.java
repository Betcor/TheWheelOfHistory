package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kolo.protocol.Protocol;
import org.junit.jupiter.api.Test;

/** Образ окремого сервера ({@code docker/Dockerfile}) узгоджений з кодом: порт, точка входу, аргументи. */
class DockerfileTest {

    /** Тести запускаються з теки модуля. */
    private static final Path DOCKER = Path.of("..", "docker");

    private static final Pattern JSON_STRING = Pattern.compile("\"([^\"]*)\"");

    @Test
    void exposesDefaultPort() throws IOException {
        assertThat(lines("Dockerfile")).contains("EXPOSE " + Protocol.DEFAULT_PORT + "/tcp");
        assertThat(lines("compose.yaml"))
                .anyMatch(line -> line.contains("\"" + Protocol.DEFAULT_PORT + ":" + Protocol.DEFAULT_PORT + "/tcp\""));
    }

    @Test
    void runsInstalledServerScript() throws IOException {
        assertThat(execForm("ENTRYPOINT")).containsExactly("/opt/kolo/bin/server");
        assertThat(lines("Dockerfile"))
                .contains("COPY --from=build /src/server/build/install/server /opt/kolo")
                .anyMatch(line -> line.endsWith(":server:installDist"));
    }

    /** Типові аргументи розбираються сервером, пошук у локальній мережі вимкнено, світи — у томі. */
    @Test
    void defaultArgumentsAreValid() throws IOException {
        DedicatedServerMain.Options options =
                DedicatedServerMain.options(execForm("CMD").toArray(String[]::new));
        assertThat(options.port()).isEqualTo(Protocol.DEFAULT_PORT);
        assertThat(options.discovery()).isEmpty();
        assertThat(options.worlds()).isEqualTo(DedicatedServerMain.DEFAULT_WORLDS);
        assertThat(lines("Dockerfile")).contains("WORKDIR /data", "VOLUME [\"/data\"]");
    }

    private static List<String> execForm(String instruction) throws IOException {
        String line = lines("Dockerfile").stream()
                .filter(l -> l.startsWith(instruction + " "))
                .reduce((first, last) -> last)
                .orElseThrow();
        Matcher matcher = JSON_STRING.matcher(line);
        return matcher.results().map(result -> result.group(1)).toList();
    }

    private static List<String> lines(String file) throws IOException {
        return Files.readAllLines(DOCKER.resolve(file)).stream()
                .map(String::strip)
                .toList();
    }
}
