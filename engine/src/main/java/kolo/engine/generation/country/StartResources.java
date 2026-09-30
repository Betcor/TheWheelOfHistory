package kolo.engine.generation.country;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.ResourceId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.RollRecord;

/**
 * Стартові родовища держави (GD §4.5) — результат {@link ResourceWheel}.
 *
 * @param deposits родовища в порядку обертань; ресурси не повторюються
 * @param rolls обертання в порядку кидків: кількість, потім ресурс кожного родовища
 */
public record StartResources(List<StartDeposit> deposits, List<RollRecord> rolls) {

    public StartResources {
        deposits = List.copyOf(deposits);
        rolls = List.copyOf(rolls);
        TreeSet<ResourceId> seen = new TreeSet<>();
        for (StartDeposit deposit : deposits) {
            if (!seen.add(deposit.resource())) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID,
                        ErrorDetails.of(
                                "field", "deposits", "value", deposit.resource().value()));
            }
        }
    }

    /** Ресурси держави — вхід колеса ядерного статусу (уран). */
    public SortedSet<ResourceId> resources() {
        TreeSet<ResourceId> resources = new TreeSet<>();
        deposits.forEach(deposit -> resources.add(deposit.resource()));
        return Collections.unmodifiableSortedSet(resources);
    }
}
