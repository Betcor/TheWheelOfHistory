package kolo.protocol.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import kolo.engine.error.ProtocolException;
import kolo.engine.error.ValidationException;
import kolo.engine.view.MapView;
import kolo.protocol.Protocol;
import kolo.protocol.TestMessages;
import org.junit.jupiter.api.Test;

class MapChunksTest {

    private static final MapView MAP = TestMessages.map(42, 10);

    @Test
    void splitsIntoStartAndOrderedChunks() {
        List<ServerMessage> messages = MapChunks.split(MAP, 4);

        assertThat(messages).hasSize(4);
        assertThat(messages.getFirst())
                .isEqualTo(new ServerMessage.MapStart(42, MAP.width(), MAP.height(), 10, MAP.countries()));
        assertThat(messages.subList(1, 4))
                .extracting(m -> ((ServerMessage.MapCells) m).first())
                .containsExactly(0, 4, 8);
        assertThat(((ServerMessage.MapCells) messages.getLast()).cells())
                .isEqualTo(MAP.cells().subList(8, 10));
    }

    @Test
    void defaultChunkSizeComesFromProtocol() {
        MapView map = TestMessages.map(1, Protocol.MAP_CHUNK_CELLS + 1);

        assertThat(MapChunks.split(map)).hasSize(3);
        assertThatThrownBy(() -> MapChunks.split(MAP, 0)).isInstanceOf(ValidationException.class);
    }

    @Test
    void assemblerRestoresTheMap() {
        MapAssembler assembler = new MapAssembler();
        List<ServerMessage> messages = MapChunks.split(MAP, 3);
        assembler.start((ServerMessage.MapStart) messages.getFirst());

        Optional<MapView> result = Optional.empty();
        for (ServerMessage message : messages.subList(1, messages.size())) {
            assertThat(result).isEmpty();
            result = assembler.add((ServerMessage.MapCells) message);
        }

        assertThat(result).contains(MAP);
        assertThat(assembler.complete()).isTrue();
        assertThat(assembler.result()).isEqualTo(MAP);
    }

    @Test
    void assemblerRejectsWrongOrder() {
        List<ServerMessage> messages = MapChunks.split(MAP, 4);
        ServerMessage.MapStart start = (ServerMessage.MapStart) messages.get(0);
        ServerMessage.MapCells first = (ServerMessage.MapCells) messages.get(1);
        ServerMessage.MapCells second = (ServerMessage.MapCells) messages.get(2);

        assertProblem(() -> new MapAssembler().add(first), "map_not_started");
        assertProblem(() -> new MapAssembler().result(), "map_incomplete");

        MapAssembler twice = new MapAssembler();
        twice.start(start);
        assertProblem(() -> twice.start(start), "map_already_started");

        MapAssembler gap = new MapAssembler();
        gap.start(start);
        assertProblem(() -> gap.add(second), "unexpected_first");

        MapAssembler repeat = new MapAssembler();
        repeat.start(start);
        repeat.add(first);
        assertProblem(() -> repeat.add(first), "unexpected_first");
        assertProblem(repeat::result, "map_incomplete");
    }

    @Test
    void assemblerRejectsExtraCells() {
        MapAssembler assembler = new MapAssembler();
        assembler.start(new ServerMessage.MapStart(1, MAP.width(), MAP.height(), 3, MAP.countries()));

        assertProblem(() -> assembler.add(new ServerMessage.MapCells(0, MAP.cells())), "too_many_cells");
    }

    @Test
    void invalidAssembledMapKeepsCause() {
        // Комірки посилаються на сусіда 3, а в зібраній карті їх лише 3 (0..2).
        MapAssembler assembler = new MapAssembler();
        assembler.start(new ServerMessage.MapStart(1, MAP.width(), MAP.height(), 3, MAP.countries()));

        assertThatThrownBy(() ->
                        assembler.add(new ServerMessage.MapCells(0, MAP.cells().subList(0, 3))))
                .isInstanceOfSatisfying(
                        ProtocolException.class,
                        e -> assertThat(e.details())
                                .containsEntry("location", "map")
                                .containsEntry("cause", "value_out_of_range"));
    }

    private static void assertProblem(Runnable action, String problem) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ProtocolException.class,
                        e -> assertThat(e.details())
                                .containsEntry("location", "map")
                                .containsEntry("problem", problem));
    }
}
