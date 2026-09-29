package kolo.engine.generation.map;

import java.util.Comparator;

/**
 * Точка карти в цілих одиницях. Порядок — рядками: спершу {@code y}, потім {@code x}, тож комірки, впорядковані за
 * центрами, йдуть смугами зверху вниз.
 */
public record GridPoint(int x, int y) implements Comparable<GridPoint> {

    private static final Comparator<GridPoint> ORDER =
            Comparator.comparingInt(GridPoint::y).thenComparingInt(GridPoint::x);

    @Override
    public int compareTo(GridPoint other) {
        return ORDER.compare(this, other);
    }
}
