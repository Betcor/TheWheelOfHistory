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
import kolo.engine.state.TurnTimer;
import kolo.engine.view.CellView;
import kolo.engine.view.CountryView;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.WorldInfo;

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
                case ClientMessage.ListLobbies list -> json.writeStringField("type", MessageTypes.LIST_LOBBIES);
                case ClientMessage.CreateLobby create -> {
                    json.writeStringField("type", MessageTypes.CREATE_LOBBY);
                    json.writeStringField("nickname", create.nickname());
                    json.writeNumberField("seed", create.seed());
                    json.writeStringField("npc_share", key(create.npcShare()));
                }
                case ClientMessage.JoinLobby join -> {
                    json.writeStringField("type", MessageTypes.JOIN_LOBBY);
                    json.writeNumberField("session", join.session());
                    json.writeStringField("nickname", join.nickname());
                }
                case ClientMessage.StartGame start -> json.writeStringField("type", MessageTypes.START_GAME);
                case ClientMessage.Rejoin rejoin -> {
                    json.writeStringField("type", MessageTypes.REJOIN);
                    json.writeNumberField("session", rejoin.session());
                    json.writeNumberField("player", rejoin.player());
                    json.writeStringField("token", rejoin.token());
                }
                case ClientMessage.Leave leave -> json.writeStringField("type", MessageTypes.LEAVE);
                case ClientMessage.Ready ready -> {
                    json.writeStringField("type", MessageTypes.READY);
                    json.writeNumberField("turn", ready.turn());
                }
                case ClientMessage.ListWorlds list -> json.writeStringField("type", MessageTypes.LIST_WORLDS);
                case ClientMessage.LoadWorld load -> {
                    json.writeStringField("type", MessageTypes.LOAD_WORLD);
                    json.writeStringField("world", load.world());
                    json.writeStringField("nickname", load.nickname());
                    json.writeFieldName("seat");
                    if (load.seat().isPresent()) {
                        json.writeStartObject();
                        json.writeNumberField("player", load.seat().get().player());
                        json.writeStringField("token", load.seat().get().token());
                        json.writeEndObject();
                    } else {
                        json.writeNull();
                    }
                }
                case ClientMessage.AssignSeat assign -> {
                    json.writeStringField("type", MessageTypes.ASSIGN_SEAT);
                    json.writeNumberField("guest", assign.guest());
                    json.writeNumberField("seat", assign.seat());
                }
                case ClientMessage.SetTimer set -> {
                    json.writeStringField("type", MessageTypes.SET_TIMER);
                    json.writeFieldName("timer");
                    timer(json, set.timer());
                }
                case ClientMessage.EndYear end -> {
                    json.writeStringField("type", MessageTypes.END_YEAR);
                    json.writeNumberField("turn", end.turn());
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
                case ServerMessage.Lobbies lobbies -> {
                    json.writeStringField("type", MessageTypes.LOBBIES);
                    json.writeArrayFieldStart("lobbies");
                    for (LobbyInfo lobby : lobbies.lobbies()) {
                        lobby(json, lobby);
                    }
                    json.writeEndArray();
                }
                case ServerMessage.Joined joined -> {
                    json.writeStringField("type", MessageTypes.JOINED);
                    json.writeNumberField("session", joined.session());
                    json.writeStringField("world", joined.world());
                    json.writeNumberField("player", joined.player());
                    json.writeStringField("token", joined.token());
                }
                case ServerMessage.Lobby lobby -> {
                    json.writeStringField("type", MessageTypes.LOBBY);
                    json.writeNumberField("session", lobby.session());
                    json.writeStringField("world", lobby.world());
                    setup(json, lobby.setup());
                    players(json, lobby.players());
                    json.writeArrayFieldStart("timers");
                    for (TurnTimer timer : lobby.timers()) {
                        timer(json, timer);
                    }
                    json.writeEndArray();
                }
                case ServerMessage.Players players -> {
                    json.writeStringField("type", MessageTypes.PLAYERS);
                    players(json, players.players());
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
                case ServerMessage.Phase phase -> {
                    json.writeStringField("type", MessageTypes.PHASE);
                    json.writeNumberField("turn", phase.turn());
                    json.writeStringField("phase", key(phase.phase()));
                    json.writeFieldName("time_left_millis");
                    if (phase.timeLeftMillis().isPresent()) {
                        json.writeNumber(phase.timeLeftMillis().getAsLong());
                    } else {
                        json.writeNull();
                    }
                }
                case ServerMessage.Worlds worlds -> {
                    json.writeStringField("type", MessageTypes.WORLDS);
                    json.writeArrayFieldStart("worlds");
                    for (WorldInfo world : worlds.worlds()) {
                        world(json, world);
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

    private static void lobby(JsonGenerator json, LobbyInfo lobby) throws IOException {
        json.writeStartObject();
        json.writeNumberField("session", lobby.session());
        json.writeStringField("world", lobby.world());
        json.writeStringField("host", lobby.host());
        json.writeNumberField("players", lobby.players());
        setup(json, lobby.setup());
        json.writeEndObject();
    }

    private static void setup(JsonGenerator json, LobbySetup setup) throws IOException {
        json.writeObjectFieldStart("setup");
        switch (setup) {
            case LobbySetup.NewWorld world -> {
                json.writeStringField("kind", MessageTypes.NEW_WORLD);
                json.writeNumberField("seed", world.seed());
                json.writeStringField("npc_share", key(world.npcShare()));
            }
            case LobbySetup.SavedWorld world -> {
                json.writeStringField("kind", MessageTypes.SAVED_WORLD);
                json.writeStringField("name", world.name());
                json.writeNumberField("seed", world.seed());
                json.writeNumberField("turn", world.turn());
            }
        }
        json.writeFieldName("timer");
        timer(json, setup.timer());
        json.writeEndObject();
    }

    private static void timer(JsonGenerator json, TurnTimer timer) throws IOException {
        json.writeStartObject();
        json.writeStringField("mode", key(timer.mode()));
        json.writeNumberField("seconds", timer.seconds());
        json.writeEndObject();
    }

    private static void world(JsonGenerator json, WorldInfo world) throws IOException {
        json.writeStartObject();
        json.writeStringField("name", world.name());
        optionalKey(json, "key", world.key().orElse(null));
        json.writeNumberField("seed", world.seed());
        json.writeNumberField("turn", world.turn());
        json.writeArrayFieldStart("players");
        for (String player : world.players()) {
            json.writeString(player);
        }
        json.writeEndArray();
        json.writeEndObject();
    }

    private static void players(JsonGenerator json, List<PlayerInfo> players) throws IOException {
        json.writeArrayFieldStart("players");
        for (PlayerInfo player : players) {
            json.writeStartObject();
            json.writeNumberField("number", player.number());
            json.writeStringField("nickname", player.nickname());
            json.writeBooleanField("host", player.host());
            json.writeBooleanField("connected", player.connected());
            json.writeBooleanField("ready", player.ready());
            optionalInt(json, "country", player.country());
            json.writeEndObject();
        }
        json.writeEndArray();
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
