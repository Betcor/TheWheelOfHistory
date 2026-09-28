package kolo.engine.state;

/**
 * Показник держави, на який може діяти модифікатор (GD §5.1).
 *
 * <p>Межі — частина моделі, а не балансу: ефективне значення завжди обрізається до них, тож модифікатори не
 * виводять показник за допустимий діапазон.
 */
public enum Stat {
    GDP(0, Long.MAX_VALUE),
    STABILITY(0, 100),
    HDI(0, 100),
    INFLUENCE(0, 100),
    LEGITIMACY(0, 100),
    WAR_WEARINESS(0, 100),
    SCIENCE(0, Integer.MAX_VALUE);

    private final long min;
    private final long max;

    Stat(long min, long max) {
        this.min = min;
        this.max = max;
    }

    public long min() {
        return min;
    }

    public long max() {
        return max;
    }

    /** Значення цього показника в наборі статів. */
    public long of(CountryStats stats) {
        return switch (this) {
            case GDP -> stats.gdp();
            case STABILITY -> stats.stability();
            case HDI -> stats.hdi();
            case INFLUENCE -> stats.influence();
            case LEGITIMACY -> stats.legitimacy();
            case WAR_WEARINESS -> stats.warWeariness();
            case SCIENCE -> stats.science();
        };
    }

    /** Обрізає значення до меж показника. */
    public long clamp(long value) {
        return Math.clamp(value, min, max);
    }
}
