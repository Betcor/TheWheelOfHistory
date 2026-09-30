package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.SaveVersionException;
import kolo.engine.state.GameMap;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;

class MapSnapshotTest {

    private static final GameMap MAP = TestWorlds.small().map();
    private static final MapSnapshot SNAPSHOT = MapSnapshot.of(MAP);

    @Test
    void readGivesTheSameMapAndHash() {
        MapSnapshot read = MapSnapshot.read(SNAPSHOT.json());

        assertThat(read.map()).isEqualTo(MAP);
        assertThat(read.hash()).isEqualTo(SNAPSHOT.hash());
        assertThat(read.json()).isEqualTo(SNAPSHOT.json());
    }

    @Test
    void sameMapGivesSameBytes() {
        assertThat(MapSnapshot.of(TestWorlds.small().map()).json()).isEqualTo(SNAPSHOT.json());
        assertThat(SNAPSHOT.hash()).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void jsonIsCompactUtf8WithVersionFirst() {
        String text = new String(SNAPSHOT.json(), StandardCharsets.UTF_8);

        assertThat(text).startsWith("{\"schema_version\":" + WorldState.SCHEMA_VERSION + ",\"width\":");
        assertThat(text).doesNotContain(" ", "\n");
    }

    @Test
    void jsonIsACopy() {
        byte[] json = SNAPSHOT.json();
        json[0] = 'x';

        assertThat(SNAPSHOT.json()[0]).isEqualTo((byte) '{');
    }

    @Test
    void differentMapDifferentHash() {
        GameMap other = new GameMap(MAP.width() + 1, MAP.height(), MAP.tiles(), MAP.seaZones());

        assertThat(MapSnapshot.of(other).hash()).isNotEqualTo(SNAPSHOT.hash());
    }

    @Test
    void invalidJsonIsMalformed() {
        assertMalformed("{\"schema_version\":1,".getBytes(StandardCharsets.UTF_8), "$", "invalid_json");
    }

    @Test
    void duplicateKeyIsMalformed() {
        byte[] json = "{\"schema_version\":1,\"schema_version\":1}".getBytes(StandardCharsets.UTF_8);

        assertMalformed(json, "$", "invalid_json");
    }

    @Test
    void newerSchemaIsRejected() {
        byte[] json = edit(root -> root.put("schema_version", WorldState.SCHEMA_VERSION + 1));

        assertThatThrownBy(() -> MapSnapshot.read(json))
                .isInstanceOfSatisfying(
                        SaveVersionException.class,
                        e -> assertThat(e.details())
                                .containsEntry("version", WorldState.SCHEMA_VERSION + 1)
                                .containsEntry("supported", WorldState.SCHEMA_VERSION));
    }

    @Test
    void olderUnknownSchemaIsMalformed() {
        assertMalformed(edit(root -> root.put("schema_version", 0)), "schema_version", "unsupported_schema_version");
    }

    @Test
    void missingFieldIsMalformed() {
        assertMalformed(edit(root -> tile(root).remove("kind")), "tiles[0].kind", "missing_field");
    }

    @Test
    void unknownFieldIsMalformed() {
        assertMalformed(edit(root -> tile(root).put("owner", 1)), "tiles[0].owner", "unknown_field");
    }

    @Test
    void wrongTypeIsMalformed() {
        assertMalformed(edit(root -> root.put("width", "wide")), "width", "expected_int");
        assertMalformed(edit(root -> root.put("width", 1.5)), "width", "expected_int");
        assertMalformed(edit(root -> root.put("width", 1L << 40)), "width", "expected_int");
        assertMalformed(edit(root -> tile(root).put("river", 1)), "tiles[0].river", "expected_boolean");
    }

    @Test
    void unknownEnumValueIsMalformed() {
        assertMalformed(edit(root -> tile(root).put("kind", "lava")), "tiles[0].kind", "unknown_value");
    }

    @Test
    void oddPolygonIsMalformed() {
        assertMalformed(
                edit(root -> ((ArrayNode) tile(root).get("polygon")).add(0)), "tiles[0].polygon", "expected_points");
    }

    @Test
    void invalidValueKeepsItsCause() {
        byte[] json = edit(root -> root.put("width", 0));

        assertThatThrownBy(() -> MapSnapshot.read(json))
                .isInstanceOfSatisfying(
                        SaveFileException.class,
                        e -> assertThat(e.details())
                                .containsEntry("part", "map")
                                .containsEntry("location", "$")
                                .containsEntry("cause", "value_out_of_range")
                                .containsEntry("field", "width"));
    }

    @Test
    void nonCanonicalJsonIsMalformed() {
        String text = new String(SNAPSHOT.json(), StandardCharsets.UTF_8);
        byte[] spaced = text.replaceFirst(",", ", ").getBytes(StandardCharsets.UTF_8);

        assertMalformed(spaced, "$", "not_canonical");
    }

    private static ObjectNode tile(ObjectNode root) {
        return (ObjectNode) root.get("tiles").get(0);
    }

    private static byte[] edit(Consumer<ObjectNode> change) {
        return SnapshotJson.edit(SNAPSHOT.json(), change);
    }

    private static void assertMalformed(byte[] json, String location, String problem) {
        assertThatThrownBy(() -> MapSnapshot.read(json)).isInstanceOfSatisfying(SaveFileException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.SAVE_MALFORMED);
            assertThat(e.details())
                    .containsEntry("part", "map")
                    .containsEntry("location", location)
                    .containsEntry("problem", problem);
        });
    }
}
