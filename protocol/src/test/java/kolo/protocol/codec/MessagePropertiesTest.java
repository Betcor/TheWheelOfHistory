package kolo.protocol.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.protocol.TestMessages;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.MapAssembler;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class MessagePropertiesTest {

    @Property(tries = 200)
    void anyMapSurvivesChunksAndJson(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 60) int cells,
            @ForAll @IntRange(min = 1, max = 70) int chunk) {
        MapView map = TestMessages.map(seed, cells);
        MapAssembler assembler = new MapAssembler();
        Optional<MapView> result = Optional.empty();

        for (ServerMessage message : MapChunks.split(map, chunk)) {
            ServerMessage read = MessageJson.readServer(MessageJson.write(message));
            assertThat(read).isEqualTo(message);
            switch (read) {
                case ServerMessage.MapStart start -> assembler.start(start);
                case ServerMessage.MapCells part -> result = assembler.add(part);
                default -> throw new AssertionError(read);
            }
        }

        assertThat(result).contains(map);
    }

    @Property(tries = 300)
    void anyErrorRoundTrips(@ForAll ErrorCode code, @ForAll("details") Map<String, Object> extra) {
        TreeMap<String, Object> details = new TreeMap<>(extra);
        code.requiredDetails().forEach(key -> details.put(key, "так"));
        ServerMessage.Error error = new ServerMessage.Error(code, details);

        assertThat(MessageJson.readServer(MessageJson.write(error))).isEqualTo(error);
    }

    @Property(tries = 300)
    void anyClientMessageRoundTrips(
            @ForAll long seed,
            @ForAll int players,
            @ForAll NpcShare share,
            @ForAll @IntRange(min = 1) int version,
            @ForAll("text") String hash,
            @ForAll @IntRange(min = 0) int turn) {
        ClientMessage create = new ClientMessage.CreateWorld(seed, players, share);
        ClientMessage hello = new ClientMessage.Hello(version, hash);
        ClientMessage ready = new ClientMessage.Ready(turn);

        assertThat(MessageJson.readClient(MessageJson.write(create))).isEqualTo(create);
        assertThat(MessageJson.readClient(MessageJson.write(hello))).isEqualTo(hello);
        assertThat(MessageJson.readClient(MessageJson.write(ready))).isEqualTo(ready);
    }

    @Property(tries = 200)
    void anyPhaseRoundTrips(@ForAll @IntRange(min = 0) int turn, @ForAll YearPhase phase) {
        ServerMessage message = new ServerMessage.Phase(turn, phase);

        assertThat(MessageJson.readServer(MessageJson.write(message))).isEqualTo(message);
    }

    @Provide
    Arbitrary<Map<String, Object>> details() {
        Arbitrary<Object> value = Arbitraries.oneOf(
                text().map(Object.class::cast),
                Arbitraries.longs().map(Object.class::cast),
                Arbitraries.integers().map(Object.class::cast),
                Arbitraries.of(true, false).map(Object.class::cast));
        return Arbitraries.maps(Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8), value)
                .ofMaxSize(5);
    }

    @Provide
    Arbitrary<String> text() {
        // Кирилиця, апострофи, лапки й керувальні символи мусять пройти JSON без змін.
        return Arbitraries.strings()
                .withCharRange('а', 'я')
                .withChars('\'', '"', '\\', '\n', '\t', 'Ї', '€', ' ')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.isBlank());
    }
}
