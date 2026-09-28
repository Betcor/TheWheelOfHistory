package kolo.engine.generation.country;

import java.util.Objects;
import java.util.Optional;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.error.Checks;
import kolo.engine.state.LocalizedName;

/**
 * Один фрагмент передісторії держави з роком, коли подія сталася.
 *
 * @param year рік у межах років фрагмента
 */
public record BackstoryEntry(BackstoryFragmentDef fragment, int year) {

    public BackstoryEntry {
        Objects.requireNonNull(fragment, "fragment");
        Checks.inRange("backstory." + fragment.id() + ".year", year, fragment.yearFrom(), fragment.yearTo());
    }

    /**
     * Текст фрагмента з назвами держави й сусіда.
     *
     * @param neighbor назва сусіда передісторії; потрібна, лише якщо фрагмент його згадує
     */
    public String text(LocalizedName country, Optional<LocalizedName> neighbor) {
        return fragment.text().render(country, neighbor, year);
    }
}
