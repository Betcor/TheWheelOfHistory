package kolo.protocol.codec;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import kolo.engine.state.GridPoint;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.view.CellView;
import kolo.engine.view.CountryView;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.ServerMessage;

/**
 * JSON повідомлень: UTF-8 без пробілів, поле {@code type} першим, решта — у сталому порядку цього класу, відсутнє
 * значення — {@code null} (поле є завжди), enum — {@code snake_case}, точка — масив {@code [x, y]}, многокутник —
 * плаский масив координат. Назви полів — частина протоколу: зміна — лише з новою {@code Protocol.VERSION}.
 */
final class MessageWriter {

    private static final JsonFactory FACTORY = new JsonFactory();

    private MessageWriter() {}

    static byte[] write(ClientMessage message) {
        return write(json -> {
            json.writeStartObject();
            switch (message) {
                case ClientMessage.Hello hello -> {
                    json.writeStringField("type", MessageTypes.HELLO);
                    json.writeNumberField("protocol_version", hello.protocolVersion());
                    json.writeStringField("content_hash", hello.contentHash());
                }
                case ClientMessage.CreateWorld create -> {
                    json.writeStringField("type", MessageTypes.CREATE_WORLD);
                    json.writeNumberField("seed", create.seed());
                    json.writeNumberField("players", create.players());
                    json.writeStringField("npc_share", key(create.npcShare()));
                }
            }
            json.writeEndObject();
        });
    }

    static byte[] write(ServerMessage message) {
        return write(json -> {
            json.writeStartObject();
            switch (message) {
                case ServerMessage.Welcome welcome -> {
                    json.writeStringField("type", MessageTypes.WELCOME);
                    json.writeNumberField("protocol_version", welcome.protocolVersion());
                    json.writeStringField("content_hash", welcome.contentHash());
                }
                case ServerMessage.Error error -> {
                    json.writeStringField("type", MessageTypes.ERROR);
                    json.writeStringField("code", key(error.code()));
                    json.writeObjectFieldStart("details");
                    for (Map.Entry<String, Object> detail : error.details().entrySet()) {
                        json.writeFieldName(detail.getKey());
                        switch (detail.getValue()) {
                            case String text -> json.writeString(text);
                            case Boolean flag -> json.writeBoolean(flag);
                            case Long number -> json.writeNumber(number);
                            // Error приводить подробиці до цих трьох типів.
                            default -> throw new IllegalStateException("подробиця " + detail);
                        }
                    }
                    json.writeEndObject();
                }
                case ServerMessage.MapStart start -> {
                    json.writeStringField("type", MessageTypes.MAP_START);
                    json.writeNumberField("seed", start.seed());
                    json.writeNumberField("width", start.width());
                    json.writeNumberField("height", start.height());
                    json.writeNumberField("cell_count", start.cellCount());
                    json.writeArrayFieldStart("countries");
                    for (CountryView country : start.countries()) {
                        country(json, country);
                    }
                    json.writeEndArray();
                }
                case ServerMessage.MapCells chunk -> {
                    json.writeStringField("type", MessageTypes.MAP_CELLS);
                    json.writeNumberField("first", chunk.first());
                    json.writeArrayFieldStart("cells");
                    for (CellView cell : chunk.cells()) {
                        cell(json, cell);
                    }
                    json.writeEndArray();
                }
            }
            json.writeEndObject();
        });
    }

    static String key(Enum<?> value) {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static void country(JsonGenerator json, CountryView country) throws IOException {
        json.writeStartObject();
        json.writeNumberField("number", country.number());
        json.writeFieldName("name");
        name(json, country.name());
        json.writeBooleanField("player", country.player());
        json.writeNumberField("provinces", country.provinces());
        json.writeEndObject();
    }

    private static void cell(JsonGenerator json, CellView cell) throws IOException {
        json.writeStartObject();
        json.writeFieldName("site");
        json.writeStartArray();
        json.writeNumber(cell.site().x());
        json.writeNumber(cell.site().y());
        json.writeEndArray();
        json.writeArrayFieldStart("polygon");
        for (GridPoint point : cell.polygon()) {
            json.writeNumber(point.x());
            json.writeNumber(point.y());
        }
        json.writeEndArray();
        ints(json, "neighbors", cell.neighbors());
        json.writeStringField("kind", key(cell.kind()));
        optionalKey(json, "terrain", cell.terrain().map(MessageWriter::key).orElse(null));
        optionalKey(json, "relief", cell.relief().map(MessageWriter::key).orElse(null));
        optionalKey(json, "climate", cell.climate().map(MessageWriter::key).orElse(null));
        optionalInt(json, "height", cell.height());
        optionalInt(json, "fertility", cell.fertility());
        json.writeBooleanField("river", cell.river());
        optionalInt(json, "downstream", cell.downstream());
        optionalInt(json, "country", cell.country());
        json.writeEndObject();
    }

    private static void name(JsonGenerator json, LocalizedName name) throws IOException {
        json.writeStartObject();
        json.writeFieldName("full");
        noun(json, name.fullName());
        json.writeFieldName("short");
        noun(json, name.shortName());
        json.writeEndObject();
    }

    private static void noun(JsonGenerator json, NounPhrase noun) throws IOException {
        json.writeStartObject();
        json.writeStringField("gender", key(noun.gender()));
        json.writeArrayFieldStart("forms");
        for (String form : noun.forms()) {
            json.writeString(form);
        }
        json.writeEndArray();
        json.writeEndObject();
    }

    // ---- Прості значення ----

    private static void ints(JsonGenerator json, String field, List<Integer> values) throws IOException {
        json.writeArrayFieldStart(field);
        for (int value : values) {
            json.writeNumber(value);
        }
        json.writeEndArray();
    }

    private static void optionalInt(JsonGenerator json, String field, OptionalInt value) throws IOException {
        json.writeFieldName(field);
        if (value.isPresent()) {
            json.writeNumber(value.getAsInt());
        } else {
            json.writeNull();
        }
    }

    private static void optionalKey(JsonGenerator json, String field, String value) throws IOException {
        json.writeFieldName(field);
        if (value != null) {
            json.writeString(value);
        } else {
            json.writeNull();
        }
    }

    private interface Body {
        void write(JsonGenerator json) throws IOException;
    }

    private static byte[] write(Body body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator json = FACTORY.createGenerator(out, JsonEncoding.UTF8)) {
            body.write(json);
        } catch (IOException e) {
            // Запис у пам'ять не має помилок вводу-виводу.
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
