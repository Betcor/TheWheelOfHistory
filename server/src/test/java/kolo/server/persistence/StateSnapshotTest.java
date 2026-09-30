package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.SaveVersionException;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.Country;
import kolo.engine.state.GameMap;
import kolo.engine.state.Province;
import kolo.engine.state.Season;
import kolo.engine.state.WorldState;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

class StateSnapshotTest {

    private static final WorldState STATE = TestWorlds.small();
    private static final MapSnapshot MAP = MapSnapshot.of(STATE.map());
    private static final StateSnapshot SNAPSHOT = StateSnapshot.of(STATE, MAP);

    @Test
    void readGivesTheSameStateAndHash() {
        StateSnapshot read = StateSnapshot.read(SNAPSHOT.json(), MAP);

        assertThat(read.state()).isEqualTo(STATE);
        assertThat(read.hash()).isEqualTo(SNAPSHOT.hash());
        assertThat(read.json()).isEqualTo(SNAPSHOT.json());
    }

    @Test
    void readsOnAMapReadFromItsSnapshot() {
        MapSnapshot map = MapSnapshot.read(MAP.json());

        assertThat(StateSnapshot.read(SNAPSHOT.json(), map).state()).isEqualTo(STATE);
    }

    @Test
    void copyOfStateGivesSameHash() {
        assertThat(StateSnapshot.of(STATE.deepCopy(), MAP).hash()).isEqualTo(SNAPSHOT.hash());
    }

    @Test
    void jsonHasMapHashInsteadOfGeometry() {
        String text = new String(SNAPSHOT.json(), StandardCharsets.UTF_8);

        assertThat(text).contains("\"map_hash\":\"" + MAP.hash() + "\"");
        assertThat(text).doesNotContain("\"polygon\"");
    }

    @Test
    void anyChangeChangesHash() {
        WorldState nextTurn = STATE.deepCopy();
        nextTurn.setTurn(STATE.turn() + 1);
        WorldState moreTokens = STATE.deepCopy();
        Country first = moreTokens.countries().firstEntry().getValue();
        first.setFateTokens(first.fateTokens() == 0 ? 1 : 0);
        WorldState tagged = STATE.deepCopy();
        tagged.provinces().firstEntry().getValue().tags().add("ruins");

        assertThat(List.of(nextTurn, moreTokens, tagged))
                .extracting(state -> StateSnapshot.of(state, MAP).hash())
                .doesNotContain(SNAPSHOT.hash())
                .doesNotHaveDuplicates();
    }

    @Test
    void optionalValuesRoundTrip() {
        WorldState state = STATE.deepCopy();
        Country country = state.countries().firstEntry().getValue();
        country.setReligion(null);
        country.modifiers()
                .add(new Modifier(
                        "event:drought:0",
                        new ModifierSource(SourceKind.EVENT, "drought"),
                        ModifierTarget.wheel(new WheelKind("economic_cycle")),
                        -15,
                        4,
                        "event.drought"));
        Province occupied = state.provinces().get(country.capital());
        occupied.setController(state.countries().lastKey());
        Province wasteland = state.provinces().values().stream()
                .filter(province -> !province.id().equals(country.capital()))
                .findFirst()
                .orElseThrow();
        wasteland.setOwner(null);
        wasteland.setController(null);
        WorldState withRoll = withWorldRoll(state, seasonRoll());

        StateSnapshot read = StateSnapshot.read(StateSnapshot.of(withRoll, MAP).json(), MAP);

        assertThat(read.state()).isEqualTo(withRoll);
        assertThat(read.state().countries().firstEntry().getValue().religion()).isEmpty();
        assertThat(read.state().generationRolls().getLast().season()).isEqualTo(Season.AUTUMN);
    }

    @Test
    void stateOnAnotherMapIsRejected() {
        GameMap map = STATE.map();
        MapSnapshot other = MapSnapshot.of(new GameMap(map.width() + 1, map.height(), map.tiles(), map.seaZones()));

        assertThatThrownBy(() -> StateSnapshot.of(STATE, other)).isInstanceOf(IllegalArgumentException.class);
        assertMalformed(SNAPSHOT.json(), other, "map_hash", "map_hash_mismatch");
    }

    @Test
    void invariantViolationIsMalformed() {
        WorldState broken = STATE.deepCopy();
        broken.countries().firstEntry().getValue().setFateTokens(99);
        byte[] json = StateSnapshot.of(broken, MAP).json();

        assertThatThrownBy(() -> StateSnapshot.read(json, MAP))
                .isInstanceOfSatisfying(
                        SaveFileException.class,
                        e -> assertThat(e.details())
                                .containsEntry("part", "state")
                                .containsEntry("location", "$")
                                .containsEntry("cause", "invariant_violation"));
    }

    @Test
    void duplicateIdIsMalformed() {
        byte[] json = edit(root -> {
            ArrayNode countries = (ArrayNode) root.get("countries");
            countries.add(countries.get(0).deepCopy());
        });
        int last = STATE.countries().size();

        assertMalformed(json, MAP, "countries[" + last + "]", "duplicate_id");
    }

    @Test
    void invalidIdKeepsItsCause() {
        byte[] json = edit(root -> ((ObjectNode) root.get("countries").get(0)).put("capital", "province_1"));

        assertThatThrownBy(() -> StateSnapshot.read(json, MAP))
                .isInstanceOfSatisfying(
                        SaveFileException.class,
                        e -> assertThat(e.details())
                                .containsEntry("location", "countries[0].capital")
                                .containsEntry("cause", "invalid_key_format"));
    }

    @Test
    void unknownFieldIsMalformed() {
        assertMalformed(
                edit(root -> ((ObjectNode) root.get("provinces").get(0)).put("fort_level", 2)),
                MAP,
                "provinces[0].fort_level",
                "unknown_field");
        assertMalformed(
                edit(root -> ((ObjectNode) root.get("countries").get(0).get("start_development")).put("magic", 1)),
                MAP,
                "countries[0].start_development.magic",
                "unknown_field");
    }

    @Test
    void unknownModifierTargetIsMalformed() {
        WorldState state = STATE.deepCopy();
        Country country = state.countries().firstEntry().getValue();
        country.modifiers().clear();
        country.modifiers()
                .add(new Modifier(
                        "event:x:0",
                        new ModifierSource(SourceKind.EVENT, "x"),
                        ModifierTarget.wheel(new WheelKind("x")),
                        1,
                        null,
                        "event.x"));
        byte[] json = SnapshotJson.edit(
                StateSnapshot.of(state, MAP).json(),
                root -> ((ObjectNode)
                                root.get("countries").get(0).get("modifiers").get(0))
                        .put("target", "province:x"));

        assertMalformed(json, MAP, "countries[0].modifiers[0].target", "unknown_value");
    }

    @Test
    void newerSchemaIsRejected() {
        byte[] json = edit(root -> root.put("schema_version", WorldState.SCHEMA_VERSION + 1));

        assertThatThrownBy(() -> StateSnapshot.read(json, MAP)).isInstanceOf(SaveVersionException.class);
    }

    @Test
    void nonCanonicalOrderIsMalformed() {
        // Той самий стан, але поля об'єкта в іншому порядку: хеш байтів відрізнявся б, тож такий запис не приймається.
        byte[] json = edit(root -> {
            ObjectNode province = (ObjectNode) root.get("provinces").get(0);
            province.set("id", province.remove("id"));
        });

        assertMalformed(json, MAP, "$", "not_canonical");
    }

    private static RollRecord seasonRoll() {
        return new RollRecord(
                new WheelKind("battle"),
                List.of(
                        new RolledSector("win", 6000, OutcomeTier.SUCCESS, 70),
                        new RolledSector("loss", 4000, OutcomeTier.FAIL, 30)),
                10,
                List.of(new AppliedModifier("army:1", "army.training", 10)),
                "loss",
                7777,
                3,
                Season.AUTUMN);
    }

    private static WorldState withWorldRoll(WorldState state, RollRecord roll) {
        List<RollRecord> rolls = new ArrayList<>(state.generationRolls());
        rolls.add(roll);
        return new WorldState(
                state.schemaVersion(),
                state.contentHash(),
                state.seed(),
                state.turn(),
                state.nextIdSeq(),
                state.map(),
                state.countries(),
                state.provinces(),
                state.people(),
                state.religions(),
                rolls);
    }

    private static byte[] edit(Consumer<ObjectNode> change) {
        return SnapshotJson.edit(SNAPSHOT.json(), change);
    }

    private static void assertMalformed(byte[] json, MapSnapshot map, String location, String problem) {
        assertThatThrownBy(() -> StateSnapshot.read(json, map)).isInstanceOfSatisfying(SaveFileException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.SAVE_MALFORMED);
            assertThat(e.details())
                    .containsEntry("part", "state")
                    .containsEntry("location", location)
                    .containsEntry("problem", problem);
        });
    }
}
