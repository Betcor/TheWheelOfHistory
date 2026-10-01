package kolo.protocol.codec;

import static kolo.protocol.codec.MessageWriter.key;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Religion;
import kolo.engine.state.TechBranch;
import kolo.engine.view.CountryCard;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;

/**
 * JSON картки держави ({@link CountryCard}) за правилами {@link MessageWriter}. Id контенту й стану — рядками, ціль
 * модифікатора — як у контенті: {@code stat:<показник>} або {@code wheel:<тип колеса>}.
 */
final class CardWriter {

    static final String STAT_PREFIX = "stat:";
    static final String WHEEL_PREFIX = "wheel:";

    private CardWriter() {}

    static void card(JsonGenerator json, CountryCard card) throws IOException {
        json.writeStartObject();
        json.writeNumberField("number", card.number());
        json.writeFieldName("name");
        MessageWriter.name(json, card.name());
        json.writeNumberField("capital", card.capital());
        json.writeNumberField("provinces", card.provinces());
        json.writeNumberField("population_k", card.populationK());
        json.writeStringField("ideology", card.ideology().value());
        json.writeStringField("sub_ideology", card.subIdeology().value());
        json.writeFieldName("religion");
        if (card.religion().isPresent()) {
            religion(json, card.religion().get());
        } else {
            json.writeNull();
        }
        json.writeArrayFieldStart("world_religions");
        for (NounPhrase name : card.worldReligions()) {
            MessageWriter.noun(json, name);
        }
        json.writeEndArray();
        json.writeObjectFieldStart("development");
        for (Map.Entry<TechBranch, Integer> branch : card.development().entrySet()) {
            json.writeNumberField(branch.getKey().key(), branch.getValue());
        }
        json.writeEndObject();
        json.writeNumberField("gdp_per_capita", card.gdpPerCapita());
        json.writeNumberField("hdi", card.hdi());
        json.writeNumberField("army_share_bp", card.armyShareBp());
        json.writeNumberField("training", card.training());
        json.writeStringField("nuclear", key(card.nuclear()));
        json.writeNumberField("warheads", card.warheads());
        json.writeNumberField("fate_tokens", card.fateTokens());
        json.writeArrayFieldStart("deposits");
        for (CountryCard.Deposit deposit : card.deposits()) {
            json.writeStartObject();
            json.writeNumberField("province", deposit.province());
            json.writeStringField("resource", deposit.resource().value());
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeArrayFieldStart("people");
        for (CountryCard.PersonCard person : card.people()) {
            person(json, person);
        }
        json.writeEndArray();
        json.writeFieldName("origin");
        origin(json, card.origin());
        json.writeArrayFieldStart("modifiers");
        for (Modifier modifier : card.modifiers()) {
            modifier(json, modifier);
        }
        json.writeEndArray();
        strings(json, "tags", card.tags());
        json.writeArrayFieldStart("rolls");
        for (RollRecord roll : card.rolls()) {
            roll(json, roll);
        }
        json.writeEndArray();
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
        MessageWriter.noun(json, religion.figure());
        json.writeFieldName("name");
        MessageWriter.noun(json, religion.name());
        strings(json, "tags", religion.tags());
        json.writeNumberField("holy_center", religion.holyCenter().number());
        json.writeEndObject();
    }

    private static void person(JsonGenerator json, CountryCard.PersonCard person) throws IOException {
        json.writeStartObject();
        json.writeFieldName("name");
        MessageWriter.name(json, person.name());
        json.writeStringField("kind", key(person.kind()));
        json.writeStringField("sex", key(person.sex()));
        strings(json, "traits", person.traits().stream().map(id -> id.value()).toList());
        json.writeNumberField("born_turn", person.bornTurn());
        json.writeBooleanField("alive", person.alive());
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
        json.writeFieldName("backstory_neighbor");
        if (origin.backstoryNeighbor().isPresent()) {
            json.writeNumber(origin.backstoryNeighbor().get().number());
        } else {
            json.writeNull();
        }
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
        json.writeStringField("source_kind", key(modifier.source().kind()));
        json.writeStringField("source_ref", modifier.source().refId());
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

    private static void roll(JsonGenerator json, RollRecord roll) throws IOException {
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
        MessageWriter.optionalKey(json, "season", roll.season() == null ? null : key(roll.season()));
        json.writeEndObject();
    }

    private static void strings(JsonGenerator json, String field, Collection<String> values) throws IOException {
        json.writeArrayFieldStart(field);
        for (String value : values) {
            json.writeString(value);
        }
        json.writeEndArray();
    }
}
