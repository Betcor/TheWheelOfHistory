package kolo.server.persistence;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
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
import kolo.engine.content.NameStyleId;
import kolo.engine.content.PopulationLevelId;
import kolo.engine.content.ReligionPolityId;
import kolo.engine.content.ResourceId;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TraitId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.SaveVersionException;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.CellKind;
import kolo.engine.state.Climate;
import kolo.engine.state.ControlType;
import kolo.engine.state.Country;
import kolo.engine.state.CountryId;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.GameMap;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.GridPoint;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.MapTile;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.Person;
import kolo.engine.state.PersonId;
import kolo.engine.state.PersonKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.Province;
import kolo.engine.state.ProvinceId;
import kolo.engine.state.Relief;
import kolo.engine.state.Religion;
import kolo.engine.state.ReligionId;
import kolo.engine.state.SeaZoneId;
import kolo.engine.state.SeaZoneState;
import kolo.engine.state.Season;
import kolo.engine.state.Sex;
import kolo.engine.state.Stat;
import kolo.engine.state.TechBranch;
import kolo.engine.state.Terrain;
import kolo.engine.state.WorldState;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.WheelKind;

/** Читання снапшота, записаного {@link SnapshotWriter}: структура та значення перевіряються суворо. */
final class SnapshotReader {

    static final String MAP = "map";
    static final String STATE = "state";

    // Дублікати ключів і зайве після кореня — помилка: інакше двоє різних файлів давали б той самий стан.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private SnapshotReader() {}

    /** Розбирає JSON і перевіряє версію схеми. */
    static SnapshotNode parse(String part, byte[] json) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            JsonLocation where = e.getLocation();
            throw new SaveFileException(
                    ErrorCode.SAVE_MALFORMED,
                    ErrorDetails.of(
                            "part",
                            part,
                            "location",
                            "$",
                            "problem",
                            "invalid_json",
                            "line",
                            where == null ? 0 : where.getLineNr(),
                            "column",
                            where == null ? 0 : where.getColumnNr()),
                    e);
        } catch (IOException e) {
            // Читання з масиву байтів не має інших помилок вводу-виводу, ніж розбору.
            throw new SaveFileException(ErrorCode.SAVE_FILE_ERROR, ErrorDetails.of("part", part), e);
        }
        if (tree == null || tree.isMissingNode()) {
            throw SnapshotNode.root(part, tree).malformed("empty");
        }
        SnapshotNode root = SnapshotNode.root(part, tree);
        SnapshotNode version = root.field("schema_version");
        int schemaVersion = version.intValue();
        if (schemaVersion > WorldState.SCHEMA_VERSION) {
            throw new SaveVersionException(
                    ErrorDetails.of("version", schemaVersion, "supported", WorldState.SCHEMA_VERSION));
        }
        // Міграцій ще немає: версія 1 — перша.
        if (schemaVersion != WorldState.SCHEMA_VERSION) {
            throw version.malformed("unsupported_schema_version");
        }
        return root;
    }

    // ---- Карта ----

    static GameMap map(SnapshotNode root) {
        int width = root.field("width").intValue();
        int height = root.field("height").intValue();
        List<MapTile> tiles = root.field("tiles").list(SnapshotReader::tile);
        List<SeaZoneState> zones = root.field("sea_zones").list(node -> {
            SeaZoneId id = node.field("id").text(SeaZoneId::new);
            List<Integer> cells = node.field("cells").ints();
            List<SeaZoneId> neighbors = node.field("neighbors").list(n -> n.text(SeaZoneId::new));
            node.end();
            return node.build(() -> new SeaZoneState(id, cells, neighbors));
        });
        root.end();
        return root.build(() -> new GameMap(width, height, tiles, zones));
    }

    private static MapTile tile(SnapshotNode node) {
        SnapshotNode siteNode = node.field("site");
        List<Integer> site = siteNode.ints();
        if (site.size() != 2) {
            throw siteNode.malformed("expected_point");
        }
        SnapshotNode polygonNode = node.field("polygon");
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
        OptionalInt continent = optionalInt(node, "continent");
        Optional<SeaZoneId> seaZone = node.optional("sea_zone").map(n -> n.text(SeaZoneId::new));
        List<SeaZoneId> coast = node.field("coast").list(n -> n.text(SeaZoneId::new));
        Optional<Terrain> terrain = node.optional("terrain").map(n -> n.enumValue(Terrain.class));
        Optional<Relief> relief = node.optional("relief").map(n -> n.enumValue(Relief.class));
        Optional<Climate> climate = node.optional("climate").map(n -> n.enumValue(Climate.class));
        OptionalInt tileHeight = optionalInt(node, "height");
        OptionalInt fertility = optionalInt(node, "fertility");
        boolean river = node.field("river").booleanValue();
        OptionalInt downstream = optionalInt(node, "downstream");
        node.end();
        return node.build(() -> new MapTile(
                new GridPoint(site.get(0), site.get(1)),
                polygon,
                neighbors,
                kind,
                continent,
                seaZone,
                coast,
                terrain,
                relief,
                climate,
                tileHeight,
                fertility,
                river,
                downstream));
    }

    // ---- Стан ----

    /** Стан на цій карті; хеш карти вже звірено. */
    static WorldState state(SnapshotNode root, GameMap map) {
        int schemaVersion = root.field("schema_version").intValue();
        String contentHash = root.field("content_hash").text();
        root.field("map_hash");
        long seed = root.field("seed").longValue();
        int turn = root.field("turn").intValue();
        long nextIdSeq = root.field("next_id_seq").longValue();
        TreeMap<CountryId, Country> countries = byId(root.field("countries"), SnapshotReader::country, Country::id);
        TreeMap<ProvinceId, Province> provinces = byId(root.field("provinces"), SnapshotReader::province, Province::id);
        TreeMap<PersonId, Person> people = byId(root.field("people"), SnapshotReader::person, Person::id);
        TreeMap<ReligionId, Religion> religions = byId(root.field("religions"), SnapshotReader::religion, Religion::id);
        List<RollRecord> rolls = root.field("generation_rolls").list(SnapshotReader::roll);
        root.end();
        return root.build(() -> new WorldState(
                schemaVersion,
                contentHash,
                seed,
                turn,
                nextIdSeq,
                map,
                countries,
                provinces,
                people,
                religions,
                rolls));
    }

    private static Country country(SnapshotNode node) {
        CountryId id = node.field("id").text(CountryId::new);
        ControlType control = node.field("control").enumValue(ControlType.class);
        LocalizedName name = name(node.field("name"));
        NameStyleId nameStyle = node.field("name_style").text(NameStyleId::new);
        ProvinceId capital = node.field("capital").text(ProvinceId::new);
        IdeologyId ideology = node.field("ideology").text(IdeologyId::new);
        SubIdeologyId subIdeology = node.field("sub_ideology").text(SubIdeologyId::new);
        ReligionId religion =
                node.optional("religion").map(n -> n.text(ReligionId::new)).orElse(null);
        SnapshotNode developmentNode = node.field("start_development");
        EnumMap<TechBranch, Integer> development = new EnumMap<>(TechBranch.class);
        for (String branch : developmentNode.fieldNames()) {
            SnapshotNode level = developmentNode.field(branch);
            development.put(techBranch(level, branch), level.intValue());
        }
        int gdpPerCapita = node.field("gdp_per_capita").intValue();
        int hdi = node.field("hdi").intValue();
        int armyShareBp = node.field("army_share_bp").intValue();
        int training = node.field("training").intValue();
        NuclearStatus nuclear = node.field("nuclear").enumValue(NuclearStatus.class);
        int warheads = node.field("warheads").intValue();
        int fateTokens = node.field("fate_tokens").intValue();
        List<Modifier> modifiers = node.field("modifiers").list(SnapshotReader::modifier);
        List<String> tags = node.field("tags").texts();
        List<PersonId> people = node.field("people").list(n -> n.text(PersonId::new));
        CountryOrigin origin = origin(node.field("origin"));
        List<RollRecord> rolls = node.field("generation_rolls").list(SnapshotReader::roll);
        node.end();
        return node.build(() -> new Country(
                id,
                control,
                name,
                nameStyle,
                capital,
                ideology,
                subIdeology,
                religion,
                development,
                gdpPerCapita,
                hdi,
                armyShareBp,
                training,
                nuclear,
                warheads,
                fateTokens,
                modifiers,
                new TreeSet<>(tags),
                people,
                origin,
                rolls));
    }

    private static TechBranch techBranch(SnapshotNode at, String key) {
        for (TechBranch branch : TechBranch.values()) {
            if (branch.key().equals(key)) {
                return branch;
            }
        }
        throw at.malformed("unknown_field");
    }

    private static CountryOrigin origin(SnapshotNode node) {
        AreaLevelId area = node.field("area").text(AreaLevelId::new);
        PopulationLevelId population = node.field("population").text(PopulationLevelId::new);
        GdpLevelId gdp = node.field("gdp").text(GdpLevelId::new);
        HdiLevelId hdi = node.field("hdi").text(HdiLevelId::new);
        ArmySizeId armySize = node.field("army_size").text(ArmySizeId::new);
        List<CountryOrigin.Backstory> backstory = node.field("backstory").list(entry -> {
            BackstoryFragmentId fragment = entry.field("fragment").text(BackstoryFragmentId::new);
            int year = entry.field("year").intValue();
            entry.end();
            return entry.build(() -> new CountryOrigin.Backstory(fragment, year));
        });
        Optional<CountryId> neighbor = node.optional("backstory_neighbor").map(n -> n.text(CountryId::new));
        List<CountryOrigin.Streak> streaks = node.field("streaks").list(entry -> {
            StreakKind kind = entry.field("kind").enumValue(StreakKind.class);
            StreakRewardId reward = entry.field("reward").text(StreakRewardId::new);
            entry.end();
            return entry.build(() -> new CountryOrigin.Streak(kind, reward));
        });
        PowerCorridor corridor = node.field("corridor").enumValue(PowerCorridor.class);
        int strengthPct = node.field("strength_pct").intValue();
        node.end();
        return node.build(() -> new CountryOrigin(
                area, population, gdp, hdi, armySize, backstory, neighbor, streaks, corridor, strengthPct));
    }

    private static Modifier modifier(SnapshotNode node) {
        String id = node.field("id").text();
        SnapshotNode sourceNode = node.field("source");
        SourceKind sourceKind = sourceNode.field("kind").enumValue(SourceKind.class);
        String refId = sourceNode.field("ref_id").text();
        sourceNode.end();
        ModifierSource source = sourceNode.build(() -> new ModifierSource(sourceKind, refId));
        ModifierTarget target = target(node.field("target"));
        int value = node.field("value").intValue();
        Integer expiresAtTurn =
                node.optional("expires_at_turn").map(SnapshotNode::intValue).orElse(null);
        String descriptionKey = node.field("description_key").text();
        node.end();
        return node.build(() -> new Modifier(id, source, target, value, expiresAtTurn, descriptionKey));
    }

    private static ModifierTarget target(SnapshotNode node) {
        String text = node.text();
        if (text.startsWith(SnapshotWriter.STAT_PREFIX)) {
            String key = text.substring(SnapshotWriter.STAT_PREFIX.length());
            for (Stat stat : Stat.values()) {
                if (SnapshotWriter.key(stat).equals(key)) {
                    return ModifierTarget.stat(stat);
                }
            }
        } else if (text.startsWith(SnapshotWriter.WHEEL_PREFIX)) {
            return node.build(
                    () -> ModifierTarget.wheel(new WheelKind(text.substring(SnapshotWriter.WHEEL_PREFIX.length()))));
        }
        throw node.malformed("unknown_value");
    }

    private static Province province(SnapshotNode node) {
        ProvinceId id = node.field("id").text(ProvinceId::new);
        CountryId owner =
                node.optional("owner").map(n -> n.text(CountryId::new)).orElse(null);
        CountryId controller =
                node.optional("controller").map(n -> n.text(CountryId::new)).orElse(null);
        int populationK = node.field("population_k").intValue();
        List<ResourceId> deposits = node.field("deposits").list(n -> n.text(ResourceId::new));
        List<String> tags = node.field("tags").texts();
        node.end();
        return node.build(
                () -> new Province(id, owner, controller, populationK, new TreeSet<>(deposits), new TreeSet<>(tags)));
    }

    private static Person person(SnapshotNode node) {
        PersonId id = node.field("id").text(PersonId::new);
        CountryId country = node.field("country").text(CountryId::new);
        LocalizedName name = name(node.field("name"));
        PersonKind kind = node.field("kind").enumValue(PersonKind.class);
        Sex sex = node.field("sex").enumValue(Sex.class);
        List<TraitId> traits = node.field("traits").list(n -> n.text(TraitId::new));
        int bornTurn = node.field("born_turn").intValue();
        boolean alive = node.field("alive").booleanValue();
        node.end();
        return node.build(() -> new Person(id, country, name, kind, sex, traits, bornTurn, alive));
    }

    private static Religion religion(SnapshotNode node) {
        ReligionId id = node.field("id").text(ReligionId::new);
        ArchetypeId archetype = node.field("archetype").text(ArchetypeId::new);
        List<AspectId> aspects = node.field("aspects").list(n -> n.text(AspectId::new));
        List<DogmaId> dogmas = node.field("dogmas").list(n -> n.text(DogmaId::new));
        ReligionPolityId polity = node.field("polity").text(ReligionPolityId::new);
        FaithFormId faithForm = node.field("faith_form").text(FaithFormId::new);
        Sex figureSex = node.field("figure_sex").enumValue(Sex.class);
        NounPhrase figure = noun(node.field("figure"));
        NounPhrase name = noun(node.field("name"));
        List<String> tags = node.field("tags").texts();
        ProvinceId holyCenter = node.field("holy_center").text(ProvinceId::new);
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
                holyCenter));
    }

    private static RollRecord roll(SnapshotNode node) {
        WheelKind kind = node.field("kind").text(WheelKind::new);
        List<RolledSector> sectors = node.field("sectors").list(sector -> {
            String id = sector.field("id").text();
            int weightBp = sector.field("weight_bp").intValue();
            OutcomeTier tier = sector.field("tier").enumValue(OutcomeTier.class);
            int quality = sector.field("quality").intValue();
            sector.end();
            return new RolledSector(id, weightBp, tier, quality);
        });
        int advantage = node.field("advantage").intValue();
        List<AppliedModifier> modifiers = node.field("modifiers").list(modifier -> {
            String sourceId = modifier.field("source_id").text();
            String descriptionKey = modifier.field("description_key").text();
            int value = modifier.field("value").intValue();
            modifier.end();
            return modifier.build(() -> new AppliedModifier(sourceId, descriptionKey, value));
        });
        String result = node.field("result").text();
        int roll = node.field("roll").intValue();
        int turn = node.field("turn").intValue();
        Season season =
                node.optional("season").map(n -> n.enumValue(Season.class)).orElse(null);
        node.end();
        return node.build(() -> new RollRecord(kind, sectors, advantage, modifiers, result, roll, turn, season));
    }

    private static LocalizedName name(SnapshotNode node) {
        NounPhrase full = noun(node.field("full"));
        NounPhrase shortName = noun(node.field("short"));
        node.end();
        return node.build(() -> new LocalizedName(full, shortName));
    }

    private static NounPhrase noun(SnapshotNode node) {
        GrammaticalGender gender = node.field("gender").enumValue(GrammaticalGender.class);
        List<String> forms = node.field("forms").texts();
        node.end();
        return node.build(() -> new NounPhrase(gender, forms));
    }

    // ---- Допоміжне ----

    private static OptionalInt optionalInt(SnapshotNode node, String field) {
        Optional<SnapshotNode> value = node.optional(field);
        return value.isPresent() ? OptionalInt.of(value.get().intValue()) : OptionalInt.empty();
    }

    /** Масив сутностей у мапу за id; повторний id — помилка, а не мовчазна заміна. */
    private static <K extends Comparable<K>, V> TreeMap<K, V> byId(
            SnapshotNode array, Function<SnapshotNode, V> read, Function<V, K> id) {
        TreeMap<K, V> values = new TreeMap<>();
        for (SnapshotNode element : array.elements()) {
            V value = read.apply(element);
            if (values.put(id.apply(value), value) != null) {
                throw element.malformed("duplicate_id");
            }
        }
        return values;
    }
}
