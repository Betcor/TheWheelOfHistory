package kolo.client.generation;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Wheel;

/**
 * Геометрія колеса для малювання: сектори — дуги за вагою за годинниковою стрілкою від стрілки вгорі, у порядку
 * секторів запису. Результат визначило число {@link RollRecord#roll()}: сектор, у чию накопичену вагу воно потрапило,
 * — тож колесо зупиняється так, що стрілка вказує саме на це число.
 */
public final class WheelGeometry {

    /** Скільки повних обертів робить колесо перед зупинкою. */
    public static final int TURNS = 4;

    private WheelGeometry() {}

    /**
     * Сектор на колесі.
     *
     * @param start початок дуги, градусів за годинниковою стрілкою від верху
     * @param extent довжина дуги, градусів
     */
    public record Slice(RolledSector sector, double start, double extent) {}

    public static List<Slice> slices(RollRecord roll) {
        List<Slice> slices = new ArrayList<>(roll.sectors().size());
        int cumulative = 0;
        for (RolledSector sector : roll.sectors()) {
            slices.add(new Slice(sector, degrees(cumulative), degrees(sector.weightBp())));
            cumulative += sector.weightBp();
        }
        return slices;
    }

    /**
     * Кут повороту колеса за годинниковою стрілкою, на якому воно зупиняється: {@link #TURNS} обертів і стрілка — на
     * середині кроку {@code roll}.
     */
    public static double finalRotation(RollRecord roll) {
        return TURNS * 360.0 - degrees(roll.roll()) - degrees(1) / 2;
    }

    /** Позиція на колесі (градусів від початку першого сектора), на яку вказує стрілка при цьому повороті. */
    public static double pointerAt(double rotation) {
        double at = -rotation % 360.0;
        return at < 0 ? at + 360.0 : at;
    }

    /** Сектор під стрілкою при цьому повороті. */
    public static RolledSector sectorAt(RollRecord roll, double rotation) {
        double at = pointerAt(rotation);
        for (Slice slice : slices(roll)) {
            if (at < slice.start() + slice.extent()) {
                return slice.sector();
            }
        }
        return roll.sectors().getLast();
    }

    /** Плавне сповільнення: частка шляху за частку часу {@code t} (0..1). */
    public static double easeOut(double t) {
        double rest = 1 - Math.clamp(t, 0, 1);
        return 1 - rest * rest * rest;
    }

    private static double degrees(int bp) {
        return bp * 360.0 / Wheel.TOTAL_BP;
    }
}
