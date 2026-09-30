package kolo.server.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/** Правка JSON снапшота в тестах: розібрати, змінити дерево, записати так само компактно. */
final class SnapshotJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SnapshotJson() {}

    static byte[] edit(byte[] json, Consumer<ObjectNode> change) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(json);
            change.accept(root);
            return MAPPER.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
