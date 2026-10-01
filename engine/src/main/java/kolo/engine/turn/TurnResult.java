package kolo.engine.turn;

import java.util.Objects;
import kolo.engine.state.WorldState;

/**
 * Підсумок року. Події й записи коліс року з'являться разом із системами, що їх породжують.
 *
 * @param newState стан наприкінці року — нова копія, вхідний стан не змінюється
 */
public record TurnResult(WorldState newState) {

    public TurnResult {
        Objects.requireNonNull(newState, "newState");
    }
}
