package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.SourceKind;
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

    /**
     * Постійні модифікатори ладу: спершу ідеології, потім підкласифікації, кожні в порядку контенту. Серед них —
     * перевага наступних коліс генерації (напр. розвиненості).
     *
     * <p>Id — {@code ideology:<id>:<номер>} і {@code sub_ideology:<id>:<номер>}; ключ пояснення — {@code
     * ideology.<id>} і {@code sub_ideology.<id>}: клієнт показує в ньому назву ладу з контенту.
     */
    public List<Modifier> modifiers() {
        List<Modifier> result = new ArrayList<>();
        add(result, "ideology", ideology.id().value(), ideology.modifiers());
        add(result, "sub_ideology", subIdeology.id().value(), subIdeology.modifiers());
        return List.copyOf(result);
    }

    private static void add(List<Modifier> target, String prefix, String refId, List<ModifierDef> defs) {
        ModifierSource source = new ModifierSource(SourceKind.IDEOLOGY, refId);
        for (int i = 0; i < defs.size(); i++) {
            target.add(defs.get(i).toModifier(prefix + ":" + refId + ":" + i, source, null, prefix + "." + refId));
        }
    }
}
