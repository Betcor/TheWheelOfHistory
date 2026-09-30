package kolo.engine.generation.country;

import java.util.Objects;
import kolo.engine.content.ResourceId;
import kolo.engine.error.Checks;

/**
 * Родовище зі стартового колеса ресурсів (GD §4.5); майбутній {@code ResourceDeposit} провінції.
 *
 * @param cell комірка карти (провінція), у якій лежить родовище
 */
public record StartDeposit(ResourceId resource, int cell) {

    public StartDeposit {
        Objects.requireNonNull(resource, "resource");
        Checks.inRange("cell", cell, 0, Integer.MAX_VALUE);
    }
}
