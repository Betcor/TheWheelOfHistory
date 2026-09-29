package kolo.engine.generation.country;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.NameStyleId;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.LocalizedName;
import kolo.engine.wheel.RollRecord;

/**
 * Назва держави на старті (GD §4.1, колесо 16; GD §4.9) — результат {@link NameWheel}.
 *
 * @param candidates назви-кандидати в порядку секторів колеса; повні назви в називному не повторюються
 * @param chosen індекс назви, що випала, з нуля
 * @param roll обертання колеса назви: сектор {@code name_<chosen + 1>}
 */
public record StartName(List<NameCandidate> candidates, int chosen, RollRecord roll) {

    public StartName {
        candidates = List.copyOf(candidates);
        Checks.inRange("name.chosen", chosen, 0, candidates.size() - 1);
        TreeSet<String> seen = new TreeSet<>();
        for (NameCandidate candidate : candidates) {
            String name = candidate.name().fullName().nominative();
            if (!seen.add(name)) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", "name.candidates", "value", name));
            }
        }
        Objects.requireNonNull(roll, "roll");
        String expected = NameWheel.sectorId(chosen);
        if (!roll.resultSectorId().equals(expected)) {
            throw new ValidationException(
                    ErrorCode.UNKNOWN_REFERENCE,
                    ErrorDetails.of("field", "name.roll", "value", roll.resultSectorId(), "expected", expected));
        }
    }

    /** Назва держави. */
    public LocalizedName name() {
        return candidates.get(chosen).name();
    }

    /** Мовний стиль назви: ним звуть людей держави. */
    public NameStyleId style() {
        return candidates.get(chosen).style();
    }
}
