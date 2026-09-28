package kolo.engine.modifier;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Звідки взявся модифікатор: вид джерела й посилання на нього.
 *
 * @param kind вид джерела
 * @param refId id джерела (ідеології, будівлі, людини, події…)
 */
public record ModifierSource(SourceKind kind, String refId) {

    public ModifierSource {
        Objects.requireNonNull(kind, "kind");
        Checks.notBlank("modifier_source.ref_id", refId);
    }
}
