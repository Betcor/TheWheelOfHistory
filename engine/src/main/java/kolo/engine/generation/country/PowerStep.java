package kolo.engine.generation.country;

import java.util.Objects;
import kolo.engine.content.PowerComponent;
import kolo.engine.error.Checks;
import kolo.engine.wheel.Advantage;

/**
 * Крок бюджету сили (GD §4.11): складник обернувся, сила перерахована.
 *
 * @param component складник
 * @param quality якість його результату, {@code 0..100}
 * @param strengthPct сила після нього, % медіани
 * @param advantage зсув наступних складників: додатний — держава заслабка, від'ємний — засильна, нуль — у коридорі
 */
public record PowerStep(PowerComponent component, int quality, int strengthPct, int advantage) {

    public PowerStep {
        Objects.requireNonNull(component, "component");
        Checks.inRange("quality", quality, 0, 100);
        Checks.inRange("strength_pct", strengthPct, 0, Integer.MAX_VALUE);
        Checks.inRange("advantage", advantage, -Advantage.MAX, Advantage.MAX);
    }
}
