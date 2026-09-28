package kolo.engine.wheel;

import java.util.List;
import java.util.Objects;
import kolo.engine.state.Season;

/**
 * Запис одного обертання колеса. Клієнт отримує його цілком: цього досить для анімації (колесо лише показує вже
 * відомий результат) і для підказки «чому такі шанси».
 *
 * @param kind тип колеса
 * @param sectors фінальні сектори в порядку контенту; сума ваг — 10 000
 * @param advantage застосована перевага
 * @param modifiers пояснення переваги
 * @param resultSectorId id сектора, що випав
 * @param roll випадкове число {@code 0..9 999}, що визначило результат
 * @param turn хід (рік = 1970 + turn)
 * @param season сезонна фаза війни; {@code null}, якщо колесо крутилося поза війною
 */
public record RollRecord(
        WheelKind kind,
        List<RolledSector> sectors,
        int advantage,
        List<AppliedModifier> modifiers,
        String resultSectorId,
        int roll,
        int turn,
        Season season) {

    public RollRecord {
        Objects.requireNonNull(kind, "kind");
        sectors = List.copyOf(sectors);
        modifiers = List.copyOf(modifiers);
        if (roll < 0 || roll >= Wheel.TOTAL_BP) {
            throw new IllegalArgumentException("кидок поза 0..9999: " + roll);
        }
        if (turn < 0) {
            throw new IllegalArgumentException("хід від'ємний: " + turn);
        }
        if (find(sectors, resultSectorId) == null) {
            throw new IllegalArgumentException("сектора " + resultSectorId + " немає в записі");
        }
    }

    /** Сектор, що випав. */
    public RolledSector result() {
        return find(sectors, resultSectorId);
    }

    private static RolledSector find(List<RolledSector> sectors, String id) {
        for (RolledSector sector : sectors) {
            if (sector.id().equals(id)) {
                return sector;
            }
        }
        return null;
    }
}
