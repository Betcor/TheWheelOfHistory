package kolo.protocol.codec;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import kolo.engine.error.ProtocolException;
import kolo.engine.error.ValidationException;
import kolo.protocol.ProtocolErrors;

/**
 * Вузол JSON повідомлення під час читання: знає своє місце ({@code cells[3].site}) і суворо перевіряє структуру —
 * кожне поле обов'язкове (відсутнє значення — {@code null}), невідоме поле, не той тип чи число поза межами типу —
 * {@link ProtocolException} з місцем.
 */
final class MessageNode {

    private final String location;
    private final JsonNode node;
    private final TreeSet<String> read = new TreeSet<>();

    private MessageNode(String location, JsonNode node) {
        this.location = location;
        this.node = node;
    }

    static MessageNode root(JsonNode node) {
        return new MessageNode("$", node);
    }

    // ---- Об'єкти ----

    /** Поле об'єкта; поле мусить бути. */
    MessageNode field(String name) {
        if (!node.isObject()) {
            throw malformed("expected_object");
        }
        JsonNode value = node.get(name);
        if (value == null) {
            throw new MessageNode(child(name), node).malformed("missing_field");
        }
        read.add(name);
        return new MessageNode(child(name), value);
    }

    /** Поле, що може бути {@code null}. */
    Optional<MessageNode> optional(String name) {
        MessageNode value = field(name);
        return value.node.isNull() ? Optional.empty() : Optional.of(value);
    }

    /** Поля об'єкта як пари «ім'я — вузол» у порядку запису; позначає їх прочитаними. */
    List<Map.Entry<String, MessageNode>> fields() {
        if (!node.isObject()) {
            throw malformed("expected_object");
        }
        List<Map.Entry<String, MessageNode>> fields = new ArrayList<>(node.size());
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            read.add(entry.getKey());
            fields.add(Map.entry(entry.getKey(), new MessageNode(child(entry.getKey()), entry.getValue())));
        }
        return fields;
    }

    /** Усі поля об'єкта прочитано; зайве поле — помилка. */
    void end() {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (!read.contains(name)) {
                throw new MessageNode(child(name), node).malformed("unknown_field");
            }
        }
    }

    // ---- Масиви ----

    List<MessageNode> elements() {
        if (!node.isArray()) {
            throw malformed("expected_array");
        }
        List<MessageNode> elements = new ArrayList<>(node.size());
        for (int i = 0; i < node.size(); i++) {
            elements.add(new MessageNode(location + "[" + i + "]", node.get(i)));
        }
        return elements;
    }

    <T> List<T> list(Function<MessageNode, T> element) {
        List<T> values = new ArrayList<>(node.size());
        for (MessageNode item : elements()) {
            values.add(element.apply(item));
        }
        return values;
    }

    List<String> texts() {
        return list(MessageNode::text);
    }

    List<Integer> ints() {
        return list(MessageNode::intValue);
    }

    // ---- Прості значення ----

    boolean isText() {
        return node.isTextual();
    }

    boolean isBoolean() {
        return node.isBoolean();
    }

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

    /** Значення enum за ключем {@link MessageWriter#key}. */
    <E extends Enum<E>> E enumValue(Class<E> type) {
        String text = text();
        for (E value : type.getEnumConstants()) {
            if (MessageWriter.key(value).equals(text)) {
                return value;
            }
        }
        throw malformed("unknown_value");
    }

    // ---- Помилки ----

    /** Будує значення моделі; помилку значення прив'язує до цього місця. */
    <T> T build(Supplier<T> build) {
        try {
            return build.get();
        } catch (ValidationException e) {
            throw ProtocolErrors.because(location, e);
        }
    }

    /** Помилка структури з цим місцем. */
    ProtocolException malformed(String problem) {
        return ProtocolErrors.malformed(location, problem);
    }

    private String child(String name) {
        return location.equals("$") ? name : location + "." + name;
    }
}
