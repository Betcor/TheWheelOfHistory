package kolo.content.loader;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DoctrineDef;
import kolo.engine.content.DoctrineId;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.error.ContentException;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Читає YAML-файли контенту й збирає з них {@link ContentPack}.
 *
 * <p>Будь-яка проблема — {@link ContentException} з ім'ям файлу й місцем у ньому: гра чи сервер із невалідним
 * контентом не стартує. Невідомі поля, повтори ключів у YAML і рядки замість чисел — теж помилки: опечатка в
 * контенті не повинна мовчки зникати.
 */
public final class ContentLoader {

    public static final String IDEOLOGIES = "ideologies.yaml";
    public static final String DOCTRINES = "doctrines.yaml";
    public static final String RESOURCES = "resources.yaml";

    /** Усі файли контенту; кожен обов'язковий. */
    public static final List<String> FILES = List.of(IDEOLOGIES, DOCTRINES, RESOURCES);

    private static final YAMLMapper MAPPER = createMapper();

    private ContentLoader() {}

    /** Контент, вбудований у гру. */
    public static ContentPack loadBundled() {
        return load(ContentSource.bundled());
    }

    public static ContentPack load(ContentSource source) {
        SortedMap<String, byte[]> files = new TreeMap<>();
        for (String file : FILES) {
            files.put(file, read(source, file));
        }

        List<IdeologyDef> ideologies = ideologies(parse(files, IDEOLOGIES, ContentYaml.IdeologiesFile.class));
        List<DoctrineDef> doctrines = doctrines(parse(files, DOCTRINES, ContentYaml.DoctrinesFile.class));
        List<ResourceDef> resources = resources(parse(files, RESOURCES, ContentYaml.ResourcesFile.class));

        // Повтори й порожні колекції вже відловлено по файлах, з місцем помилки; тут — лише збирання.
        String hash = ContentHash.of(files);
        return at("*", "", () -> new ContentPack(hash, ideologies, doctrines, resources));
    }

    // ---- Файли ----

    private static byte[] read(ContentSource source, String file) {
        Optional<byte[]> content;
        try {
            content = source.read(file);
        } catch (IOException e) {
            throw new ContentException(ErrorCode.CONTENT_READ_FAILED, ErrorDetails.of("file", file), e);
        }
        return content.orElseThrow(
                () -> new ContentException(ErrorCode.CONTENT_FILE_MISSING, ErrorDetails.of("file", file)));
    }

    private static <T> T parse(SortedMap<String, byte[]> files, String file, Class<T> type) {
        try {
            T parsed = MAPPER.readValue(files.get(file), type);
            if (parsed == null) {
                // Порожній файл: нуль документів YAML.
                throw malformed(file, JsonLocation.NA, "порожній файл");
            }
            return parsed;
        } catch (JsonMappingException e) {
            // Рядок у Jackson — позиція після проблемного токена, тож шлях у файлі точніший.
            ContentException error = malformed(file, e.getLocation(), e.getOriginalMessage(), e);
            TreeMap<String, Object> details = new TreeMap<>(error.details());
            details.put("location", path(e.getPath()));
            throw new ContentException(ErrorCode.CONTENT_MALFORMED, details, e);
        } catch (JsonProcessingException e) {
            throw malformed(file, e.getLocation(), e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new ContentException(ErrorCode.CONTENT_READ_FAILED, ErrorDetails.of("file", file), e);
        }
    }

    // ---- Перетворення YAML → модель рушія ----

    private static List<IdeologyDef> ideologies(ContentYaml.IdeologiesFile yaml) {
        TreeSet<IdeologyId> ids = new TreeSet<>();
        TreeSet<SubIdeologyId> subIds = new TreeSet<>();
        return list(
                IDEOLOGIES,
                "ideologies",
                nonEmpty(IDEOLOGIES, "ideologies", yaml.ideologies()),
                (location, ideology) -> {
                    IdeologyId id = at(IDEOLOGIES, location, () -> unique(ids, new IdeologyId(ideology.id())));
                    List<ModifierDef> modifiers = modifiers(IDEOLOGIES, location, ideology.modifiers());
                    List<SubIdeologyDef> subs = list(
                            IDEOLOGIES, location + ".sub_ideologies", ideology.subIdeologies(), (subLocation, sub) -> {
                                SubIdeologyId subId =
                                        at(IDEOLOGIES, subLocation, () -> unique(subIds, new SubIdeologyId(sub.id())));
                                List<ModifierDef> subModifiers = modifiers(IDEOLOGIES, subLocation, sub.modifiers());
                                return at(
                                        IDEOLOGIES,
                                        subLocation,
                                        () -> new SubIdeologyDef(subId, sub.name(), subModifiers, sub.tags()));
                            });
                    return at(
                            IDEOLOGIES,
                            location,
                            () -> new IdeologyDef(id, ideology.name(), modifiers, ideology.tags(), subs));
                });
    }

    private static List<DoctrineDef> doctrines(ContentYaml.DoctrinesFile yaml) {
        TreeSet<DoctrineId> ids = new TreeSet<>();
        return list(
                DOCTRINES, "doctrines", nonEmpty(DOCTRINES, "doctrines", yaml.doctrines()), (location, doctrine) -> {
                    DoctrineId id = at(DOCTRINES, location, () -> unique(ids, new DoctrineId(doctrine.id())));
                    List<ModifierDef> modifiers = modifiers(DOCTRINES, location, doctrine.modifiers());
                    return at(
                            DOCTRINES,
                            location,
                            () -> new DoctrineDef(id, doctrine.name(), modifiers, doctrine.tags()));
                });
    }

    private static List<ResourceDef> resources(ContentYaml.ResourcesFile yaml) {
        TreeSet<ResourceId> ids = new TreeSet<>();
        return list(
                RESOURCES, "resources", nonEmpty(RESOURCES, "resources", yaml.resources()), (location, resource) -> {
                    ResourceId id = at(RESOURCES, location, () -> unique(ids, new ResourceId(resource.id())));
                    return at(RESOURCES, location, () -> new ResourceDef(id, resource.name(), resource.tags()));
                });
    }

    private static List<ModifierDef> modifiers(String file, String owner, List<ContentYaml.Modifier> yaml) {
        return list(
                file,
                owner + ".modifiers",
                yaml,
                (location, modifier) -> at(
                        file,
                        location,
                        () -> new ModifierDef(ModifierTargets.parse(modifier.target()), modifier.value())));
    }

    /** Перетворює кожен елемент списку, передаючи його місце у файлі, напр. {@code ideologies[2]}. */
    private static <Y, T> List<T> list(String file, String field, List<Y> items, Element<Y, T> convert) {
        List<T> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            String location = field + "[" + i + "]";
            Y item = items.get(i);
            if (item == null) {
                throw invalid(
                        file,
                        location,
                        new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", field)));
            }
            result.add(convert.apply(location, item));
        }
        return result;
    }

    /** Верхній список файлу: порожній означає, що колесо генерації не матиме жодного сектора. */
    private static <Y> List<Y> nonEmpty(String file, String field, List<Y> items) {
        if (items.isEmpty()) {
            throw invalid(
                    file, field, new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field)));
        }
        return items;
    }

    @FunctionalInterface
    private interface Element<Y, T> {
        T apply(String location, Y item);
    }

    private static <K extends Comparable<K>> K unique(TreeSet<K> seen, K id) {
        if (!seen.add(id)) {
            throw new ValidationException(ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", "id", "value", id));
        }
        return id;
    }

    // ---- Помилки ----

    /** Будує значення моделі; помилку значення прив'язує до файлу й місця в ньому. */
    private static <T> T at(String file, String location, Supplier<T> build) {
        try {
            return build.get();
        } catch (ValidationException e) {
            throw invalid(file, location, e);
        }
    }

    private static ContentException invalid(String file, String location, ValidationException cause) {
        TreeMap<String, Object> details = new TreeMap<>(cause.details());
        details.put("file", file);
        details.put("location", location);
        details.put("cause", cause.code().name().toLowerCase(Locale.ROOT));
        return new ContentException(ErrorCode.INVALID_CONTENT, details, cause);
    }

    /** Шлях у форматі {@code doctrines[0].name}, як у {@link #invalid}. */
    private static String path(List<JsonMappingException.Reference> references) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : references) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static ContentException malformed(String file, JsonLocation location, String problem) {
        return malformed(file, location, problem, null);
    }

    private static ContentException malformed(String file, JsonLocation location, String problem, Throwable cause) {
        JsonLocation where = location == null ? JsonLocation.NA : location;
        return new ContentException(
                ErrorCode.CONTENT_MALFORMED,
                ErrorDetails.of(
                        "file",
                        file,
                        "line",
                        where.getLineNr(),
                        "column",
                        where.getColumnNr(),
                        "problem",
                        String.valueOf(problem)),
                cause);
    }

    private static YAMLMapper createMapper() {
        YAMLMapper mapper = YAMLMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        // «value: "5"» чи «name: 5» — найімовірніше помилка автора контенту, а не намір.
        for (CoercionInputShape shape :
                List.of(CoercionInputShape.String, CoercionInputShape.Float, CoercionInputShape.Boolean)) {
            mapper.coercionConfigFor(LogicalType.Integer).setCoercion(shape, CoercionAction.Fail);
        }
        for (CoercionInputShape shape :
                List.of(CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean)) {
            mapper.coercionConfigFor(LogicalType.Textual).setCoercion(shape, CoercionAction.Fail);
        }
        return mapper;
    }
}
