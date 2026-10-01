package kolo.protocol.codec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.CellKind;
import kolo.engine.state.Climate;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.GridPoint;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.NpcShare;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;
import kolo.engine.view.CellView;
import kolo.engine.view.CountryView;
import kolo.protocol.ProtocolErrors;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;

/** Читання повідомлень, записаних {@link MessageWriter}: структура й значення перевіряються суворо. */
final class MessageReader {

    // Дублікати ключів і зайве після кореня — помилка: повідомлення має одне прочитання.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private MessageReader() {}

    static ClientMessage client(byte[] json) {
        MessageNode root = parse(json);
        MessageNode type = root.field("type");
        ClientMessage message = switch (type.text()) {
            case MessageTypes.HELLO -> {
                int version = root.field("protocol_version").intValue();
                String hash = root.field("content_hash").text();
                yield root.build(() -> new ClientMessage.Hello(version, hash));
            }
            case MessageTypes.CREATE_WORLD -> {
                long seed = root.field("seed").longValue();
                int players = root.field("players").intValue();
                NpcShare share = root.field("npc_share").enumValue(NpcShare.class);
                yield root.build(() -> new ClientMessage.CreateWorld(seed, players, share));
            }
            case MessageTypes.READY -> {
                int turn = root.field("turn").intValue();
                yield root.build(() -> new ClientMessage.Ready(turn));
            }
            default -> throw type.malformed("unknown_type");
        };
        root.end();
        return message;
    }

    static ServerMessage server(byte[] json) {
        MessageNode root = parse(json);
        MessageNode type = root.field("type");
        ServerMessage message = switch (type.text()) {
            case MessageTypes.WELCOME -> {
                int version = root.field("protocol_version").intValue();
                String hash = root.field("content_hash").text();
                yield root.build(() -> new ServerMessage.Welcome(version, hash));
            }
            case MessageTypes.ERROR -> error(root);
            case MessageTypes.MAP_START -> {
                long seed = root.field("seed").longValue();
                int width = root.field("width").intValue();
                int height = root.field("height").intValue();
                int cellCount = root.field("cell_count").intValue();
                List<CountryView> countries = root.field("countries").list(MessageReader::country);
                yield root.build(() -> new ServerMessage.MapStart(seed, width, height, cellCount, countries));
            }
            case MessageTypes.MAP_CELLS -> {
                int first = root.field("first").intValue();
                List<CellView> cells = root.field("cells").list(MessageReader::cell);
                yield root.build(() -> new ServerMessage.MapCells(first, cells));
            }
            case MessageTypes.PHASE -> {
                int turn = root.field("turn").intValue();
                YearPhase phase = root.field("phase").enumValue(YearPhase.class);
                yield root.build(() -> new ServerMessage.Phase(turn, phase));
            }
            default -> throw type.malformed("unknown_type");
        };
        root.end();
        return message;
    }

    private static MessageNode parse(byte[] json) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw ProtocolErrors.malformed("$", "invalid_json");
        } catch (IOException e) {
            // Читання з масиву байтів не має інших помилок вводу-виводу, ніж розбору.
            throw new UncheckedIOException(e);
        }
        if (tree == null || tree.isMissingNode()) {
            throw ProtocolErrors.malformed("$", "empty");
        }
        return MessageNode.root(tree);
    }

    private static ServerMessage.Error error(MessageNode root) {
        ErrorCode code = root.field("code").enumValue(ErrorCode.class);
        TreeMap<String, Object> details = new TreeMap<>();
        for (Map.Entry<String, MessageNode> detail : root.field("details").fields()) {
            MessageNode value = detail.getValue();
            Object parsed;
            if (value.isText()) {
                parsed = value.text();
            } else if (value.isBoolean()) {
                parsed = value.booleanValue();
            } else {
                parsed = value.longValue();
            }
            details.put(detail.getKey(), parsed);
        }
        return root.build(() -> new ServerMessage.Error(code, details));
    }

    private static CountryView country(MessageNode node) {
        int number = node.field("number").intValue();
        LocalizedName name = name(node.field("name"));
        boolean player = node.field("player").booleanValue();
        int provinces = node.field("provinces").intValue();
        node.end();
        return node.build(() -> new CountryView(number, name, player, provinces));
    }

    private static CellView cell(MessageNode node) {
        MessageNode siteNode = node.field("site");
        List<Integer> site = siteNode.ints();
        if (site.size() != 2) {
            throw siteNode.malformed("expected_point");
        }
        MessageNode polygonNode = node.field("polygon");
        List<Integer> coordinates = polygonNode.ints();
        if (coordinates.size() % 2 != 0) {
            throw polygonNode.malformed("expected_points");
        }
        List<GridPoint> polygon = new ArrayList<>(coordinates.size() / 2);
        for (int i = 0; i < coordinates.size(); i += 2) {
            polygon.add(new GridPoint(coordinates.get(i), coordinates.get(i + 1)));
        }
        List<Integer> neighbors = node.field("neighbors").ints();
        CellKind kind = node.field("kind").enumValue(CellKind.class);
        Optional<Terrain> terrain = node.optional("terrain").map(n -> n.enumValue(Terrain.class));
        Optional<Relief> relief = node.optional("relief").map(n -> n.enumValue(Relief.class));
        Optional<Climate> climate = node.optional("climate").map(n -> n.enumValue(Climate.class));
        OptionalInt height = optionalInt(node, "height");
        OptionalInt fertility = optionalInt(node, "fertility");
        boolean river = node.field("river").booleanValue();
        OptionalInt downstream = optionalInt(node, "downstream");
        OptionalInt country = optionalInt(node, "country");
        node.end();
        return node.build(() -> new CellView(
                new GridPoint(site.get(0), site.get(1)),
                polygon,
                neighbors,
                kind,
                terrain,
                relief,
                climate,
                height,
                fertility,
                river,
                downstream,
                country));
    }

    private static LocalizedName name(MessageNode node) {
        NounPhrase full = noun(node.field("full"));
        NounPhrase shortName = noun(node.field("short"));
        node.end();
        return node.build(() -> new LocalizedName(full, shortName));
    }

    private static NounPhrase noun(MessageNode node) {
        GrammaticalGender gender = node.field("gender").enumValue(GrammaticalGender.class);
        List<String> forms = node.field("forms").texts();
        node.end();
        return node.build(() -> new NounPhrase(gender, forms));
    }

    private static OptionalInt optionalInt(MessageNode node, String field) {
        Optional<MessageNode> value = node.optional(field);
        return value.isPresent() ? OptionalInt.of(value.get().intValue()) : OptionalInt.empty();
    }
}
