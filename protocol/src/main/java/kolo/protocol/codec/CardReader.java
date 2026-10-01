package kolo.protocol.codec;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import kolo.engine.content.ArchetypeId;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.AspectId;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.DogmaId;
import kolo.engine.content.FaithFormId;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.HdiLevelId;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.PopulationLevelId;
import kolo.engine.content.ReligionPolityId;
import kolo.engine.content.ResourceId;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TraitId;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.CountryId;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.ProvinceId;
import kolo.engine.state.Religion;
import kolo.engine.state.ReligionId;
import kolo.engine.state.Season;
import kolo.engine.state.Sex;
import kolo.engine.state.Stat;
import kolo.engine.state.TechBranch;
import kolo.engine.view.CountryCard;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.WheelKind;

/** Читання картки держави, записаної {@link CardWriter}. */
final class CardReader {

    private CardReader() {}

    static CountryCard card(MessageNode node) {
        int number = node.field("number").intValue();
        LocalizedName name = MessageReader.name(node.field("name"));
        int capital = node.field("capital").intValue();
        int provinces = node.field("provinces").intValue();
        long populationK = node.field("population_k").longValue();
        IdeologyId ideology = id(node.field("ideology"), IdeologyId::new);
        SubIdeologyId subIdeology = id(node.field("sub_ideology"), SubIdeologyId::new);
        Optional<Religion> religion = node.optional("religion").map(CardReader::religion);
        List<NounPhrase> worldReligions = node.field("world_religions").list(MessageReader::noun);
        Map<TechBranch, Integer> development = development(node.field("development"));
        int gdpPerCapita = node.field("gdp_per_capita").intValue();
        int hdi = node.field("hdi").intValue();
        int armyShareBp = node.field("army_share_bp").intValue();
        int training = node.field("training").intValue();
        NuclearStatus nuclear = node.field("nuclear").enumValue(NuclearStatus.class);
        int warheads = node.field("warheads").intValue();
        int fateTokens = node.field("fate_tokens").intValue();
        List<CountryCard.Deposit> deposits = node.field("deposits").list(CardReader::deposit);
        List<CountryCard.PersonCard> people = node.field("people").list(CardReader::person);
        CountryOrigin origin = origin(node.field("origin"));
        List<Modifier> modifiers = node.field("modifiers").list(CardReader::modifier);
        List<String> tags = node.field("tags").texts();
        List<RollRecord> rolls = node.field("rolls").list(CardReader::roll);
        node.end();
        return node.build(() -> new CountryCard(
                number,
                name,
                capital,
                provinces,
                populationK,
                ideology,
                subIdeology,
                religion,
                worldReligions,
                development,
                gdpPerCapita,
                hdi,
                armyShareBp,
                training,
                nuclear,
                warheads,
                fateTokens,
                deposits,
                people,
                origin,
                modifiers,
                new TreeSet<>(tags),
                rolls));
    }

    private static Map<TechBranch, Integer> development(MessageNode node) {
        EnumMap<TechBranch, Integer> development = new EnumMap<>(TechBranch.class);
        for (Map.Entry<String, MessageNode> entry : node.fields()) {
            TechBranch branch = null;
            for (TechBranch candidate : TechBranch.values()) {
                if (candidate.key().equals(entry.getKey())) {
                    branch = candidate;
                }
            }
            if (branch == null) {
                throw entry.getValue().malformed("unknown_value");
            }
            development.put(branch, entry.getValue().intValue());
        }
        return development;
    }

    private static Religion religion(MessageNode node) {
        ReligionId id = id(node.field("id"), ReligionId::new);
        ArchetypeId archetype = id(node.field("archetype"), ArchetypeId::new);
        List<AspectId> aspects = ids(node.field("aspects"), AspectId::new);
        List<DogmaId> dogmas = ids(node.field("dogmas"), DogmaId::new);
        ReligionPolityId polity = id(node.field("polity"), ReligionPolityId::new);
        FaithFormId faithForm = id(node.field("faith_form"), FaithFormId::new);
        Sex figureSex = node.field("figure_sex").enumValue(Sex.class);
        NounPhrase figure = MessageReader.noun(node.field("figure"));
        NounPhrase name = MessageReader.noun(node.field("name"));
        List<String> tags = node.field("tags").texts();
        long holyCenter = node.field("holy_center").longValue();
        node.end();
        return node.build(() -> new Religion(
                id,
                archetype,
                aspects,
                dogmas,
                polity,
                faithForm,
                figureSex,
                figure,
                name,
                new TreeSet<>(tags),
                ProvinceId.of(holyCenter)));
    }

    private static CountryCard.Deposit deposit(MessageNode node) {
        int province = node.field("province").intValue();
        ResourceId resource = id(node.field("resource"), ResourceId::new);
        node.end();
        return node.build(() -> new CountryCard.Deposit(province, resource));
    }

    private static CountryCard.PersonCard person(MessageNode node) {
        LocalizedName name = MessageReader.name(node.field("name"));
        PersonKind kind = node.field("kind").enumValue(PersonKind.class);
        Sex sex = node.field("sex").enumValue(Sex.class);
        List<TraitId> traits = ids(node.field("traits"), TraitId::new);
        int bornTurn = node.field("born_turn").intValue();
        boolean alive = node.field("alive").booleanValue();
        node.end();
        return node.build(() -> new CountryCard.PersonCard(name, kind, sex, traits, bornTurn, alive));
    }

    private static CountryOrigin origin(MessageNode node) {
        AreaLevelId area = id(node.field("area"), AreaLevelId::new);
        PopulationLevelId population = id(node.field("population"), PopulationLevelId::new);
        GdpLevelId gdp = id(node.field("gdp"), GdpLevelId::new);
        HdiLevelId hdi = id(node.field("hdi"), HdiLevelId::new);
        ArmySizeId armySize = id(node.field("army_size"), ArmySizeId::new);
        List<CountryOrigin.Backstory> backstory = node.field("backstory").list(entry -> {
            BackstoryFragmentId fragment = id(entry.field("fragment"), BackstoryFragmentId::new);
            int year = entry.field("year").intValue();
            entry.end();
            return entry.build(() -> new CountryOrigin.Backstory(fragment, year));
        });
        Optional<MessageNode> neighborNode = node.optional("backstory_neighbor");
        Optional<CountryId> neighbor = neighborNode.isPresent()
                ? Optional.of(neighborNode
                        .get()
                        .build(() -> CountryId.of(neighborNode.get().longValue())))
                : Optional.empty();
        List<CountryOrigin.Streak> streaks = node.field("streaks").list(entry -> {
            StreakKind kind = entry.field("kind").enumValue(StreakKind.class);
            StreakRewardId reward = id(entry.field("reward"), StreakRewardId::new);
            entry.end();
            return entry.build(() -> new CountryOrigin.Streak(kind, reward));
        });
        PowerCorridor corridor = node.field("corridor").enumValue(PowerCorridor.class);
        int strengthPct = node.field("strength_pct").intValue();
        node.end();
        return node.build(() -> new CountryOrigin(
                area, population, gdp, hdi, armySize, backstory, neighbor, streaks, corridor, strengthPct));
    }

    private static Modifier modifier(MessageNode node) {
        String id = node.field("id").text();
        SourceKind sourceKind = node.field("source_kind").enumValue(SourceKind.class);
        String sourceRef = node.field("source_ref").text();
        ModifierTarget target = target(node.field("target"));
        int value = node.field("value").intValue();
        Integer expiresAtTurn =
                node.optional("expires_at_turn").map(MessageNode::intValue).orElse(null);
        String descriptionKey = node.field("description_key").text();
        node.end();
        return node.build(() -> new Modifier(
                id, new ModifierSource(sourceKind, sourceRef), target, value, expiresAtTurn, descriptionKey));
    }

    private static ModifierTarget target(MessageNode node) {
        String text = node.text();
        if (text.startsWith(CardWriter.STAT_PREFIX)) {
            String key = text.substring(CardWriter.STAT_PREFIX.length());
            for (Stat stat : Stat.values()) {
                if (MessageWriter.key(stat).equals(key)) {
                    return ModifierTarget.stat(stat);
                }
            }
        } else if (text.startsWith(CardWriter.WHEEL_PREFIX)) {
            return node.build(
                    () -> ModifierTarget.wheel(new WheelKind(text.substring(CardWriter.WHEEL_PREFIX.length()))));
        }
        throw node.malformed("unknown_value");
    }

    private static RollRecord roll(MessageNode node) {
        WheelKind kind = id(node.field("kind"), WheelKind::new);
        List<RolledSector> sectors = node.field("sectors").list(sector -> {
            String id = sector.field("id").text();
            int weight = sector.field("weight_bp").intValue();
            OutcomeTier tier = sector.field("tier").enumValue(OutcomeTier.class);
            int quality = sector.field("quality").intValue();
            sector.end();
            return sector.build(() -> new RolledSector(id, weight, tier, quality));
        });
        int advantage = node.field("advantage").intValue();
        List<AppliedModifier> modifiers = node.field("modifiers").list(modifier -> {
            String source = modifier.field("source_id").text();
            String description = modifier.field("description_key").text();
            int value = modifier.field("value").intValue();
            modifier.end();
            return modifier.build(() -> new AppliedModifier(source, description, value));
        });
        String result = node.field("result").text();
        int roll = node.field("roll").intValue();
        int turn = node.field("turn").intValue();
        Season season =
                node.optional("season").map(n -> n.enumValue(Season.class)).orElse(null);
        node.end();
        return node.build(() -> new RollRecord(kind, sectors, advantage, modifiers, result, roll, turn, season));
    }

    private interface IdOf<T> {
        T of(String value);
    }

    private static <T> T id(MessageNode node, IdOf<T> of) {
        String value = node.text();
        return node.build(() -> of.of(value));
    }

    private static <T> List<T> ids(MessageNode node, IdOf<T> of) {
        return node.list(element -> id(element, of));
    }
}
