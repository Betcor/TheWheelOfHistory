package kolo.server.persistence;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.Country;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.GameMap;
import kolo.engine.state.GridPoint;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.MapTile;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Person;
import kolo.engine.state.Province;
import kolo.engine.state.Religion;
import kolo.engine.state.SeaZoneState;
import kolo.engine.state.TechBranch;
import kolo.engine.state.WorldState;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;

/**
 * Канонічний JSON снапшота: UTF-8 без пробілів, поля — у сталому порядку цього класу, мапи стану — масивами в
 * порядку id, відсутнє значення — {@code null} (поле є завжди). Той самий стан — ті самі байти на будь-якій JVM, тож
 * хеш байтів — хеш стану.
 *
 * <p>Порядок і назви полів — частина формату збереження: зміна — лише з новою версією схеми й міграцією.
 */
final class SnapshotWriter {

    static final String STAT_PREFIX = "stat:";
    static final String WHEEL_PREFIX = "wheel:";

    private static final JsonFactory FACTORY = new JsonFactory();

    private SnapshotWriter() {}

    static byte[] map(GameMap map) {
        return write(json -> {
            json.writeStartObject();
            json.writeNumberField("schema_version", WorldState.SCHEMA_VERSION);
            json.writeNumberField("width", map.width());
            json.writeNumberField("height", map.height());
            json.writeArrayFieldStart("tiles");
            for (MapTile tile : map.tiles()) {
                tile(json, tile);
            }
            json.writeEndArray();
            json.writeArrayFieldStart("sea_zones");
            for (SeaZoneState zone : map.seaZones()) {
                json.writeStartObject();
                json.writeStringField("id", zone.id().value());
                ints(json, "cells", zone.cells());
                strings(
                        json,
                        "neighbors",
                        zone.neighbors().stream().map(id -> id.value()).toList());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        });
    }

    static byte[] state(WorldState state, String mapHash) {
        return write(json -> {
            json.writeStartObject();
            json.writeNumberField("schema_version", state.schemaVersion());
            json.writeStringField("content_hash", state.contentHash());
            json.writeStringField("map_hash", mapHash);
            json.writeNumberField("seed", state.seed());
            json.writeNumberField("turn", state.turn());
            json.writeNumberField("next_id_seq", state.nextIdSeq());
            json.writeArrayFieldStart("countries");
            for (Country country : state.countries().values()) {
                country(json, country);
            }
            json.writeEndArray();
            json.writeArrayFieldStart("provinces");
            for (Province province : state.provinces().values()) {
                province(json, province);
            }
            json.writeEndArray();
            json.writeArrayFieldStart("people");
            for (Person person : state.people().values()) {
                person(json, person);
            }
            json.writeEndArray();
            json.writeArrayFieldStart("religions");
            for (Religion religion : state.religions().values()) {
                religion(json, religion);
            }
            json.writeEndArray();
            rolls(json, "generation_rolls", state.generationRolls());
            json.writeEndObject();
        });
    }

    /** Ключ значення enum у снапшоті, як у контенті: {@code energy_science}. */
    static String key(Enum<?> value) {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return value.name().toLowerCase(Locale.ROOT);
    }

    // ---- Карта ----

    private static void tile(JsonGenerator json, MapTile tile) throws IOException {
        json.writeStartObject();
        json.writeArrayFieldStart("site");
        json.writeNumber(tile.site().x());
        json.writeNumber(tile.site().y());
        json.writeEndArray();
        // Плаский масив x0, y0, x1, y1…: многокутники — найбільша частина карти.
        json.writeArrayFieldStart("polygon");
        for (GridPoint point : tile.polygon()) {
            json.writeNumber(point.x());
            json.writeNumber(point.y());
        }
        json.writeEndArray();
        ints(json, "neighbors", tile.neighbors());
        json.writeStringField("kind", key(tile.kind()));
        optionalInt(json, "continent", tile.continent());
        optionalString(json, "sea_zone", tile.seaZone().map(id -> id.value()));
        strings(json, "coast", tile.coast().stream().map(id -> id.value()).toList());
        optionalString(json, "terrain", tile.terrain().map(SnapshotWriter::key));
        optionalString(json, "relief", tile.relief().map(SnapshotWriter::key));
        optionalString(json, "climate", tile.climate().map(SnapshotWriter::key));
        optionalInt(json, "height", tile.height());
        optionalInt(json, "fertility", tile.fertility());
        json.writeBooleanField("river", tile.river());
        optionalInt(json, "downstream", tile.downstream());
        json.writeEndObject();
    }

    // ---- Стан ----

    private static void country(JsonGenerator json, Country country) throws IOException {
        json.writeStartObject();
        json.writeStringField("id", country.id().value());
        json.writeStringField("control", key(country.control()));
        json.writeFieldName("name");
        name(json, country.name());
        json.writeStringField("name_style", country.nameStyle().value());
        json.writeStringField("capital", country.capital().value());
        json.writeStringField("ideology", country.ideology().value());
        json.writeStringField("sub_ideology", country.subIdeology().value());
        optionalString(json, "religion", country.religion().map(id -> id.value()));
        json.writeObjectFieldStart("start_development");
        Map<TechBranch, Integer> development = country.startDevelopment();
        for (TechBranch branch : TechBranch.values()) {
            Integer level = development.get(branch);
            if (level != null) {
                json.writeNumberField(branch.key(), level);
            }
        }
        json.writeEndObject();
        json.writeNumberField("gdp_per_capita", country.gdpPerCapita());
        json.writeNumberField("hdi", country.hdi());
        json.writeNumberField("army_share_bp", country.armyShareBp());
        json.writeNumberField("training", country.training());
        json.writeStringField("nuclear", key(country.nuclear()));
        json.writeNumberField("warheads", country.warheads());
        json.writeNumberField("fate_tokens", country.fateTokens());
        json.writeArrayFieldStart("modifiers");
        for (Modifier modifier : country.modifiers()) {
            modifier(json, modifier);
        }
        json.writeEndArray();
        strings(json, "tags", country.tags());
        strings(json, "people", country.people().stream().map(id -> id.value()).toList());
        json.writeFieldName("origin");
        origin(json, country.origin());
        rolls(json, "generation_rolls", country.generationRolls());
        json.writeEndObject();
    }

    private static void origin(JsonGenerator json, CountryOrigin origin) throws IOException {
        json.writeStartObject();
        json.writeStringField("area", origin.area().value());
        json.writeStringField("population", origin.population().value());
        json.writeStringField("gdp", origin.gdp().value());
        json.writeStringField("hdi", origin.hdi().value());
        json.writeStringField("army_size", origin.armySize().value());
        json.writeArrayFieldStart("backstory");
        for (CountryOrigin.Backstory entry : origin.backstory()) {
            json.writeStartObject();
            json.writeStringField("fragment", entry.fragment().value());
            json.writeNumberField("year", entry.year());
            json.writeEndObject();
        }
        json.writeEndArray();
        optionalString(json, "backstory_neighbor", origin.backstoryNeighbor().map(id -> id.value()));
        json.writeArrayFieldStart("streaks");
        for (CountryOrigin.Streak streak : origin.streaks()) {
            json.writeStartObject();
            json.writeStringField("kind", key(streak.kind()));
            json.writeStringField("reward", streak.reward().value());
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeStringField("corridor", key(origin.corridor()));
        json.writeNumberField("strength_pct", origin.strengthPct());
        json.writeEndObject();
    }

    private static void modifier(JsonGenerator json, Modifier modifier) throws IOException {
        json.writeStartObject();
        json.writeStringField("id", modifier.id());
        json.writeObjectFieldStart("source");
        json.writeStringField("kind", key(modifier.source().kind()));
        json.writeStringField("ref_id", modifier.source().refId());
        json.writeEndObject();
        // Як ціль модифікатора в контенті: stat:<показник> або wheel:<тип колеса>.
        json.writeStringField(
                "target",
                switch (modifier.target()) {
                    case ModifierTarget.StatTarget stat -> STAT_PREFIX + key(stat.stat());
                    case ModifierTarget.WheelTarget wheel ->
                        WHEEL_PREFIX + wheel.kind().id();
                });
        json.writeNumberField("value", modifier.value());
        json.writeFieldName("expires_at_turn");
        if (modifier.expiresAtTurn() == null) {
            json.writeNull();
        } else {
            json.writeNumber(modifier.expiresAtTurn());
        }
        json.writeStringField("description_key", modifier.descriptionKey());
        json.writeEndObject();
    }

    private static void province(JsonGenerator json, Province province) throws IOException {
        json.writeStartObject();
        json.writeStringField("id", province.id().value());
        optionalString(json, "owner", province.owner().map(id -> id.value()));
        optionalString(json, "controller", province.controller().map(id -> id.value()));
        json.writeNumberField("population_k", province.populationK());
        strings(
                json,
                "deposits",
                province.deposits().stream().map(id -> id.value()).toList());
        strings(json, "tags", province.tags());
        json.writeEndObject();
    }

    private static void person(JsonGenerator json, Person person) throws IOException {
        json.writeStartObject();
        json.writeStringField("id", person.id().value());
        json.writeStringField("country", person.country().value());
        json.writeFieldName("name");
        name(json, person.name());
        json.writeStringField("kind", key(person.kind()));
        json.writeStringField("sex", key(person.sex()));
        strings(json, "traits", person.traits().stream().map(id -> id.value()).toList());
        json.writeNumberField("born_turn", person.bornTurn());
        json.writeBooleanField("alive", person.alive());
        json.writeEndObject();
    }

    private static void religion(JsonGenerator json, Religion religion) throws IOException {
        json.writeStartObject();
        json.writeStringField("id", religion.id().value());
        json.writeStringField("archetype", religion.archetype().value());
        strings(
                json,
                "aspects",
                religion.aspects().stream().map(id -> id.value()).toList());
        strings(json, "dogmas", religion.dogmas().stream().map(id -> id.value()).toList());
        json.writeStringField("polity", religion.polity().value());
        json.writeStringField("faith_form", religion.faithForm().value());
        json.writeStringField("figure_sex", key(religion.figureSex()));
        json.writeFieldName("figure");
        noun(json, religion.figure());
        json.writeFieldName("name");
        noun(json, religion.name());
        strings(json, "tags", religion.tags());
        json.writeStringField("holy_center", religion.holyCenter().value());
        json.writeEndObject();
    }

    private static void rolls(JsonGenerator json, String field, List<RollRecord> rolls) throws IOException {
        json.writeArrayFieldStart(field);
        for (RollRecord roll : rolls) {
            json.writeStartObject();
            json.writeStringField("kind", roll.kind().id());
            json.writeArrayFieldStart("sectors");
            for (RolledSector sector : roll.sectors()) {
                json.writeStartObject();
                json.writeStringField("id", sector.id());
                json.writeNumberField("weight_bp", sector.weightBp());
                json.writeStringField("tier", key(sector.tier()));
                json.writeNumberField("quality", sector.quality());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeNumberField("advantage", roll.advantage());
            json.writeArrayFieldStart("modifiers");
            for (AppliedModifier modifier : roll.modifiers()) {
                json.writeStartObject();
                json.writeStringField("source_id", modifier.sourceId());
                json.writeStringField("description_key", modifier.descriptionKey());
                json.writeNumberField("value", modifier.value());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeStringField("result", roll.resultSectorId());
            json.writeNumberField("roll", roll.roll());
            json.writeNumberField("turn", roll.turn());
            optionalString(json, "season", Optional.ofNullable(roll.season()).map(SnapshotWriter::key));
            json.writeEndObject();
        }
        json.writeEndArray();
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
        strings(json, "forms", noun.forms());
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

    private static void strings(JsonGenerator json, String field, Collection<String> values) throws IOException {
        json.writeArrayFieldStart(field);
        for (String value : values) {
            json.writeString(value);
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

    private static void optionalString(JsonGenerator json, String field, Optional<String> value) throws IOException {
        json.writeFieldName(field);
        if (value.isPresent()) {
            json.writeString(value.get());
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
