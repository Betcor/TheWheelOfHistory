package kolo.server.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.GameException;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.ValidationException;

/**
 * Вузол JSON снапшота під час читання: знає своє місце ({@code countries[3].capital}) і суворо перевіряє структуру —
 * кожне поле обов'язкове (відсутнє значення — {@code null}), невідоме поле, не той тип чи число поза {@code int} —
 * помилка {@link ErrorCode#SAVE_MALFORMED} з місцем.
 */
final class SnapshotNode {

    private final String part;
    private final String location;
    private final JsonNode node;
    private final TreeSet<String> read = new TreeSet<>();

    private SnapshotNode(String part, String location, JsonNode node) {
        this.part = part;
        this.location = location;
        this.node = node;
    }

    /** @param part частина снапшота для подробиць помилки: {@code map} чи {@code state} */
    static SnapshotNode root(String part, JsonNode node) {
        return new SnapshotNode(part, "$", node);
    }

    // ---- Об'єкти ----

    /** Поле об'єкта; поле мусить бути. */
    SnapshotNode field(String name) {
        if (!node.isObject()) {
            throw malformed("expected_object");
        }
        JsonNode value = node.get(name);
        if (value == null) {
            throw new SnapshotNode(part, child(name), node).malformed("missing_field");
        }
        read.add(name);
        return new SnapshotNode(part, child(name), value);
    }

    /** Поле, що може бути {@code null}. */
    Optional<SnapshotNode> optional(String name) {
        SnapshotNode value = field(name);
        return value.node.isNull() ? Optional.empty() : Optional.of(value);
    }

    /** Імена полів об'єкта в порядку запису; позначає їх прочитаними. */
    List<String> fieldNames() {
        if (!node.isObject()) {
            throw malformed("expected_object");
        }
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        read.addAll(names);
        return names;
    }

    /** Усі поля об'єкта прочитано; зайве поле — помилка. */
    void end() {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (!read.contains(name)) {
                throw new SnapshotNode(part, child(name), node).malformed("unknown_field");
            }
        }
    }

    // ---- Масиви ----

    List<SnapshotNode> elements() {
        if (!node.isArray()) {
            throw malformed("expected_array");
        }
        List<SnapshotNode> elements = new ArrayList<>(node.size());
        for (int i = 0; i < node.size(); i++) {
            elements.add(new SnapshotNode(part, location + "[" + i + "]", node.get(i)));
        }
        return elements;
    }

    <T> List<T> list(Function<SnapshotNode, T> element) {
        List<T> values = new ArrayList<>(node.size());
        for (SnapshotNode item : elements()) {
            values.add(element.apply(item));
        }
        return values;
    }

    List<String> texts() {
        return list(SnapshotNode::text);
    }

    List<Integer> ints() {
        return list(SnapshotNode::intValue);
    }

    // ---- Прості значення ----

    String text() {
        if (!node.isTextual()) {
            throw malformed("expected_string");
        }
        return node.textValue();
    }

    int intValue() {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw malformed("expected_int");
        }
        return node.intValue();
    }

    long longValue() {
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw malformed("expected_long");
        }
        return node.longValue();
    }

    boolean booleanValue() {
        if (!node.isBoolean()) {
            throw malformed("expected_boolean");
        }
        return node.booleanValue();
    }

    /** Значення enum за ключем {@link SnapshotWriter#key}. */
    <E extends Enum<E>> E enumValue(Class<E> type) {
        String text = text();
        for (E value : type.getEnumConstants()) {
            if (SnapshotWriter.key(value).equals(text)) {
                return value;
            }
        }
        throw malformed("unknown_value");
    }

    /** Значення з текстом вузла; помилку значення (формат id, межі) прив'язує до місця. */
    <T> T text(Function<String, T> build) {
        String text = text();
        return build(() -> build.apply(text));
    }

    // ---- Помилки ----

    /** Будує значення моделі; помилку значення прив'язує до цього місця. */
    <T> T build(Supplier<T> build) {
        try {
            return build.get();
        } catch (ValidationException e) {
            throw because(e);
        }
    }

    /** Помилка структури з цим місцем. */
    SaveFileException malformed(String problem) {
        return new SaveFileException(
                ErrorCode.SAVE_MALFORMED, ErrorDetails.of("part", part, "location", location, "problem", problem));
    }

    /** Помилка значення з цим місцем: код і подробиці первинної помилки зберігаються. */
    SaveFileException because(GameException cause) {
        TreeMap<String, Object> details = new TreeMap<>(cause.details());
        details.put("part", part);
        details.put("location", location);
        details.put("cause", cause.code().name().toLowerCase(Locale.ROOT));
        return new SaveFileException(ErrorCode.SAVE_MALFORMED, details, cause);
    }

    private String child(String name) {
        return location.equals("$") ? name : location + "." + name;
    }
}
