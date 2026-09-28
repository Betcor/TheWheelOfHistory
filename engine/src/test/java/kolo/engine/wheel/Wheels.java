package kolo.engine.wheel;

import java.util.List;

/** Тестові колеса. */
final class Wheels {

    /** Звичайне будівництво з дизайну: КП 3%, П 12%, ЧУ 25%, У 50%, КУ 10%. */
    static final List<Sector<String>> CONSTRUCTION = List.of(
            sector("crit_fail", 300, OutcomeTier.CRIT_FAIL),
            sector("fail", 1200, OutcomeTier.FAIL),
            sector("partial", 2500, OutcomeTier.PARTIAL),
            sector("success", 5000, OutcomeTier.SUCCESS),
            sector("crit_success", 1000, OutcomeTier.CRIT_SUCCESS));

    static final WheelKind CONSTRUCTION_KIND = new WheelKind("construction");

    private Wheels() {}

    static Sector<String> sector(String id, int weightBp, OutcomeTier tier) {
        return new Sector<>(id, weightBp, id, tier.ordinal() * 25, tier, List.of());
    }

    static int[] weights(List<? extends Sector<?>> sectors) {
        int[] weights = new int[sectors.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = sectors.get(i).weightBp();
        }
        return weights;
    }
}
