package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Передісторія (GD §4.7): фрагменти й словник міток, які дають колеса генерації.
 *
 * <p>Мітки в умовах фрагментів мають звідкись братися: з ідеологій, ядерних статусів, інших фрагментів або з
 * {@link #generationTags()}. Інакше опечатка в мітці мовчки зробила б фрагмент недосяжним. Цю перевірку робить
 * {@link ContentPack}: лише він знає ідеології.
 *
 * @see BackstoryFragmentDef
 */
public final class BackstoryContent {

    private final SortedMap<String, String> generationTags;
    private final SortedMap<BackstoryFragmentId, BackstoryFragmentDef> fragments;

    /**
     * @param generationTags мітки, які дають колеса генерації (географія, ВВП, армія…), з описом: коли колесо дає
     *     мітку
     * @throws ValidationException якщо фрагментів немає, id повторюється, мітка не {@code snake_case} або опис
     *     порожній
     */
    public BackstoryContent(Map<String, String> generationTags, List<BackstoryFragmentDef> fragments) {
        TreeMap<String, String> tags = new TreeMap<>();
        for (Map.Entry<String, String> tag : generationTags.entrySet()) {
            Checks.snakeCase("generation_tags", tag.getKey());
            tags.put(tag.getKey(), Checks.notBlank("generation_tags." + tag.getKey(), tag.getValue()));
        }
        this.generationTags = Collections.unmodifiableSortedMap(tags);

        if (fragments.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "backstory"));
        }
        TreeMap<BackstoryFragmentId, BackstoryFragmentDef> byId = new TreeMap<>();
        for (BackstoryFragmentDef fragment : fragments) {
            Objects.requireNonNull(fragment, "backstory");
            if (byId.putIfAbsent(fragment.id(), fragment) != null) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", "backstory", "value", fragment.id()));
            }
        }
        this.fragments = Collections.unmodifiableSortedMap(byId);
    }

    /** Мітки коліс генерації з описом, за міткою. */
    public SortedMap<String, String> generationTags() {
        return generationTags;
    }

    public SortedMap<BackstoryFragmentId, BackstoryFragmentDef> fragments() {
        return fragments;
    }

    public Optional<BackstoryFragmentDef> fragment(BackstoryFragmentId id) {
        return Optional.ofNullable(fragments.get(id));
    }

    /** Мітки, які дає сама передісторія: словник коліс генерації й мітки фрагментів. */
    public SortedSet<String> producedTags() {
        TreeSet<String> produced = new TreeSet<>(generationTags.keySet());
        fragments.values().forEach(fragment -> produced.addAll(fragment.adds()));
        return Collections.unmodifiableSortedSet(produced);
    }

    /** Фрагменти, доступні державі з цими мітками, за id. */
    public List<BackstoryFragmentDef> available(Set<String> tags, boolean hasNeighbor) {
        Objects.requireNonNull(tags, "tags");
        return fragments.values().stream()
                .filter(fragment -> fragment.available(tags, hasNeighbor))
                .toList();
    }
}
