package kolo.engine.modifier;

import java.util.Objects;

/**
 * Баф або дебаф.
 *
 * <p>Термін дії: модифікатор з {@code expiresAtTurn = T} діє протягом ходу {@code T} включно й знімається в кінці
 * цього ходу ({@link Modifiers#withoutExpired}). Так модифікатор «на 3 роки», виданий у ході {@code t}, має
 * {@code expiresAtTurn = t + 2}.
 *
 * @param id унікальний id модифікатора
 * @param source джерело
 * @param target на що діє
 * @param value внесок: в одиницях показника або в пунктах переваги; може бути від'ємним
 * @param expiresAtTurn останній хід дії; {@code null} — безстроковий
 * @param descriptionKey ключ i18n тексту пояснення
 */
public record Modifier(
        String id,
        ModifierSource source,
        ModifierTarget target,
        int value,
        Integer expiresAtTurn,
        String descriptionKey) {

    public Modifier {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("порожній id модифікатора");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (expiresAtTurn != null && expiresAtTurn < 0) {
            throw new IllegalArgumentException("термін модифікатора " + id + " від'ємний: " + expiresAtTurn);
        }
        if (descriptionKey == null || descriptionKey.isBlank()) {
            throw new IllegalArgumentException("порожній descriptionKey модифікатора " + id);
        }
    }

    /** Чи діє модифікатор у ході {@code turn}. */
    public boolean isActiveAt(int turn) {
        return expiresAtTurn == null || turn <= expiresAtTurn;
    }
}
