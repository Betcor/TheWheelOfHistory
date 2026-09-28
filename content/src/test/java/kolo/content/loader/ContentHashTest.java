package kolo.content.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ContentHashTest {

    @Test
    void knownVectorForEmptyInput() {
        // SHA-256 порожнього рядка: жодного файлу — нічого не хешується.
        assertThat(ContentHash.of(new TreeMap<>()))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void normalizesOnlyCrlf() {
        assertThat(hash("a.yaml", "x\r\ny\n")).isEqualTo(hash("a.yaml", "x\ny\n"));
        assertThat(hash("a.yaml", "x\ry")).as("одиночний CR лишається").isNotEqualTo(hash("a.yaml", "xy"));
    }

    @Test
    void fileBoundariesAndNamesMatter() {
        TreeMap<String, byte[]> split = new TreeMap<>();
        split.put("a.yaml", bytes("ab"));
        split.put("b.yaml", bytes("c"));
        TreeMap<String, byte[]> shifted = new TreeMap<>();
        shifted.put("a.yaml", bytes("a"));
        shifted.put("b.yaml", bytes("bc"));

        assertThat(ContentHash.of(split)).isNotEqualTo(ContentHash.of(shifted));
        assertThat(hash("a.yaml", "x")).isNotEqualTo(hash("b.yaml", "x"));
    }

    private static String hash(String name, String content) {
        TreeMap<String, byte[]> files = new TreeMap<>();
        files.put(name, bytes(content));
        return ContentHash.of(files);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
