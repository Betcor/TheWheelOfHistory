package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.RollRecord;

/**
 * Лад держави — ідеологія й підкласифікація (GD §4.1, колеса 5–6), результат {@link RegimeWheel}.
 *
 * @param subIdeology підкласифікація, що належить {@code ideology}
 * @param tags мітки ідеології й підкласифікації разом — вхід для наступних коліс генерації
 * @param rolls обертання в порядку кидків: ідеологія, потім підкласифікація
 */
public record Regime(IdeologyDef ideology, SubIdeologyDef subIdeology, SortedSet<String> tags, List<RollRecord> rolls) {

    public Regime {
        Objects.requireNonNull(ideology, "ideology");
        Objects.requireNonNull(subIdeology, "subIdeology");
        if (ideology.subIdeology(subIdeology.id()).isEmpty()) {
            throw new ValidationException(
                    ErrorCode.UNKNOWN_REFERENCE,
                    ErrorDetails.of(
                            "field", "ideology." + ideology.id() + ".sub_ideologies", "value", subIdeology.id()));
        }
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        rolls = List.copyOf(rolls);
    }
}
