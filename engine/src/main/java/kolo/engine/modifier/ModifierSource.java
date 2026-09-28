package kolo.engine.modifier;

import java.util.Objects;

/**
 * Звідки взявся модифікатор: вид джерела й посилання на нього.
 *
 * @param kind вид джерела
 * @param refId id джерела (ідеології, будівлі, людини, події…)
 */
public record ModifierSource(SourceKind kind, String refId) {

    public ModifierSource {
        Objects.requireNonNull(kind, "kind");
        if (refId == null || refId.isBlank()) {
            throw new IllegalArgumentException("порожній refId джерела модифікатора");
        }
    }
}
