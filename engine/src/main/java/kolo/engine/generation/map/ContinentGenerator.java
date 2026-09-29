package kolo.engine.generation.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import kolo.engine.content.ContentPack;
import kolo.engine.content.ContinentsDef;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Материки (GD §3.5): ділить комірки сітки на суходіл і море так, що материків рівно стільки, скільки дав розмір
 * світу, а провінцій суходолу — рівно {@link WorldSize#provinces()}.
 *
 * <ol>
 *   <li>{@link #SIZE_KIND} — для кожного материка колесо ваги: рівні сектори {@code size_<n>} у діапазоні контенту.
 *       Кожен материк отримує мінімум провінцій, решта ділиться пропорційно вагам (найбільші залишки, при рівності —
 *       менший номер).
 *   <li>Зерна — якомога далі одне від одного й від краю карти: кожне — рівноймовірно серед комірок, чий простір
 *       (відстань у кроках по сусідах до обраних зерен або подвоєна — до краю) не менший за
 *       {@value #SEED_SPREAD_PCT}% найбільшого.
 *   <li>Ріст: щоразу росте материк, що набрав найменшу частку своєї цілі; він бере з черги найдешевшу вільну
 *       комірку. Ціна — відстань від зерна й шум, змішані за порізаністю берегів; комірка на краю карти —
 *       найостанніша. Комірка, що торкається іншого материка, пропускається: між материками завжди море.
 * </ol>
 *
 * <p>Якщо материк замкнули інші й він не може дорости, розкладка повторюється з новими зернами й шумом (до
 * {@value #MAX_ATTEMPTS} спроб). Ваги материків не залежать від спроби. Колеса нейтральні: сектори
 * {@link OutcomeTier#PARTIAL}, без переваги.
 */
public final class ContinentGenerator {

    public static final WheelKind SIZE_KIND = new WheelKind("world_continent_size");

    /** Зерно наступного материка — не ближче за цю частку найбільшої можливої відстані до вже обраних. */
    static final int SEED_SPREAD_PCT = 75;

    /** Спроб розкладки: на вбудованому контенті вистачає першої, повтор — запас для тісних карт. */
    static final int MAX_ATTEMPTS = 20;

    /** Ціна комірки на краю карти: більша за будь-яку відстань із шумом, тож край заповнюється останнім. */
    private static final long EDGE_PENALTY = 1L << 40;

    /**
     * Середнє трьох октав шуму значень скупчене біля середини (типове відхилення — чверть півдіапазону): розтяг утричі
     * з обрізанням дає множнику весь діапазон порізаності.
     */
    static final int NOISE_CONTRAST = 3;

    /** Комірка в записі черги — молодші біти; {@code MapGridDef.MAX_CELLS} менший. */
    private static final int CELL_BITS = 15;

    private static final long CELL_MASK = (1L << CELL_BITS) - 1;

    private ContinentGenerator() {}

    /**
     * @param rng окремий потік материків; всередині — {@code size:<n>} на колесо ваги кожного материка й
     *     {@code layout:<спроба>} на розкладку ({@code seeds}, {@code noise})
     * @param grid сітка, щонайменше з {@link WorldSize#provinces()} комірок; зазвичай
     *     {@link MapTemplateDef#gridCells(int)}
     * @throws ValidationException якщо шаблону розміру світу немає в контенті ({@link ErrorCode#UNKNOWN_REFERENCE}),
     *     провінцій менше, ніж мінімум на кожен материк, або комірок менше, ніж провінцій
     *     ({@link ErrorCode#VALUE_OUT_OF_RANGE})
     * @throws InvariantViolationException якщо жодна з {@value #MAX_ATTEMPTS} спроб не розклала материки
     */
    public static ContinentMap generate(Rng rng, ContentPack content, WorldSize size, MapGrid grid) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(grid, "grid");
        content.map()
                .template(size.template())
                .orElseThrow(() -> new ValidationException(
                        ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", "template", "value", size.template())));
        ContinentsDef def = content.map().continents();
        int count = size.continents();
        int provinces = size.provinces();
        long minimum = (long) count * def.minProvinces();
        if (provinces < minimum) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE,
                    ErrorDetails.of("field", "provinces", "value", provinces, "min", minimum));
        }
        Checks.inRange("cells", grid.cells().size(), provinces, Integer.MAX_VALUE);

        List<RollRecord> rolls = new ArrayList<>();
        int[] weights = new int[count];
        int strength = content.balance().wheel().strength(SIZE_KIND);
        for (int i = 0; i < count; i++) {
            WheelSpin<Integer> spin = Wheel.spin(
                    rng.fork("size:" + i),
                    SIZE_KIND,
                    WorldSizeWheel.rangeSectors("size_", def.sizeWeight()),
                    Advantage.NONE,
                    strength,
                    0,
                    null);
            rolls.add(spin.record());
            weights[i] = spin.value();
        }
        int[] targets = targets(provinces, def.minProvinces(), weights);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Optional<Layout> layout = layout(rng.fork("layout:" + attempt), grid, def, targets);
            if (layout.isPresent()) {
                return result(layout.get(), weights, rolls);
            }
        }
        throw new InvariantViolationException(ErrorDetails.of(
                "field", "continents", "value", count, "cells", grid.cells().size(), "provinces", provinces));
    }

    /** Кожному материку мінімум, решта — пропорційно вагам методом найбільших залишків; при рівності — менший номер. */
    static int[] targets(int provinces, int minimum, int[] weights) {
        int count = weights.length;
        long weightSum = Arrays.stream(weights).asLongStream().sum();
        long rest = provinces - (long) count * minimum;
        int[] targets = new int[count];
        long[] remainders = new long[count];
        long given = 0;
        for (int i = 0; i < count; i++) {
            long share = rest * weights[i];
            targets[i] = minimum + (int) (share / weightSum);
            remainders[i] = share % weightSum;
            given += share / weightSum;
        }
        for (long left = rest - given; left > 0; left--) {
            int best = 0;
            for (int i = 1; i < count; i++) {
                if (remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            targets[best]++;
            remainders[best] = -1;
        }
        return targets;
    }

    /** Одна спроба: зерна й власник кожної комірки або порожньо, якщо якийсь материк не доріс. */
    private static Optional<Layout> layout(Rng rng, MapGrid grid, ContinentsDef def, int[] targets) {
        int count = targets.length;
        int cells = grid.cells().size();
        Optional<int[]> seeds = seeds(rng.fork("seeds"), grid, count);
        if (seeds.isEmpty()) {
            return Optional.empty();
        }
        ValueNoise noise = new ValueNoise(rng.fork("noise").nextLong());
        // Середня сторона комірки: з неї — масштаб плям шуму і його внесок у ціну.
        int side = Math.max(1, (int) Math.sqrt((double) grid.width() * grid.height() / cells));
        int period = side * def.noiseCells();

        int[] owners = new int[cells];
        Arrays.fill(owners, ContinentMap.SEA);
        int[] filled = new int[count];
        boolean[][] queued = new boolean[count][cells];
        List<PriorityQueue<Long>> frontiers = new ArrayList<>(count);
        for (int c = 0; c < count; c++) {
            frontiers.add(new PriorityQueue<>());
        }
        Grower grower = new Grower(grid, def, noise, period, seeds.get(), owners, filled, queued, frontiers);
        for (int c = 0; c < count; c++) {
            if (grower.touchesOther(seeds.get()[c], c)) {
                return Optional.empty();
            }
            grower.claim(c, seeds.get()[c]);
        }

        long remaining = Arrays.stream(targets).asLongStream().sum() - count;
        for (; remaining > 0; remaining--) {
            int c = hungriest(filled, targets);
            if (!grower.grow(c)) {
                return Optional.empty();
            }
        }
        return Optional.of(new Layout(seeds.get(), owners));
    }

    /** Материк із найменшою часткою набраного від цілі серед недоповнених; при рівності — менший номер. */
    private static int hungriest(int[] filled, int[] targets) {
        int best = -1;
        for (int c = 0; c < filled.length; c++) {
            if (filled[c] >= targets[c]) {
                continue;
            }
            if (best < 0 || (long) filled[c] * targets[best] < (long) filled[best] * targets[c]) {
                best = c;
            }
        }
        return best;
    }

    /**
     * Зерна, якомога далі одне від одного й від краю карти. Простір — відстань у кроках по сусідах до найближчого
     * обраного зерна або подвоєна — до краю: море між двома материками спільне, а біля краю — лише одного. Кожне зерно
     * — рівноймовірно серед комірок, чий простір не менший за {@value #SEED_SPREAD_PCT}% найбільшого.
     */
    static Optional<int[]> seeds(Rng rng, MapGrid grid, int count) {
        int cells = grid.cells().size();
        List<Integer> edges = new ArrayList<>();
        for (int i = 0; i < cells; i++) {
            if (grid.cells().get(i).edge()) {
                edges.add(i);
            }
        }
        int[] fromEdge =
                distances(grid, edges.stream().mapToInt(Integer::intValue).toArray());
        int[] seeds = new int[count];
        for (int s = 0; s < count; s++) {
            int[] fromSeeds = distances(grid, Arrays.copyOf(seeds, s));
            int[] room = new int[cells];
            int widest = 0;
            for (int cell = 0; cell < cells; cell++) {
                // +1: на крихітній карті, де всі комірки на краю, зерно все одно знайдеться.
                room[cell] = (int) Math.min(fromSeeds[cell], 2L * fromEdge[cell] + 1);
                widest = Math.max(widest, room[cell]);
            }
            // Усі комірки вже зерна: місця для ще одного немає.
            if (widest == 0) {
                return Optional.empty();
            }
            int threshold = Math.max(1, Math.ceilDiv(widest * SEED_SPREAD_PCT, 100));
            List<Integer> candidates = new ArrayList<>();
            for (int cell = 0; cell < cells; cell++) {
                if (room[cell] >= threshold) {
                    candidates.add(cell);
                }
            }
            seeds[s] = candidates.get(rng.nextInt(candidates.size()));
        }
        return Optional.of(seeds);
    }

    /** Відстань у кроках по сусідах від найближчого джерела; недосяжні — {@link Integer#MAX_VALUE}. */
    static int[] distances(MapGrid grid, int[] sources) {
        int[] distance = new int[grid.cells().size()];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int source : sources) {
            distance[source] = 0;
            queue.add(source);
        }
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (distance[neighbor] == Integer.MAX_VALUE) {
                    distance[neighbor] = distance[cell] + 1;
                    queue.add(neighbor);
                }
            }
        }
        return distance;
    }

    private static ContinentMap result(Layout layout, int[] weights, List<RollRecord> rolls) {
        List<List<Integer>> cells = new ArrayList<>();
        for (int c = 0; c < weights.length; c++) {
            cells.add(new ArrayList<>());
        }
        List<Integer> cellContinents = new ArrayList<>(layout.owners().length);
        for (int cell = 0; cell < layout.owners().length; cell++) {
            int owner = layout.owners()[cell];
            cellContinents.add(owner);
            if (owner != ContinentMap.SEA) {
                cells.get(owner).add(cell);
            }
        }
        List<Continent> continents = new ArrayList<>(weights.length);
        for (int c = 0; c < weights.length; c++) {
            continents.add(new Continent(layout.seeds()[c], weights[c], cells.get(c)));
        }
        return new ContinentMap(continents, cellContinents, rolls);
    }

    /** Вдала спроба: зерно кожного материка й власник кожної комірки. */
    private record Layout(int[] seeds, int[] owners) {}

    /** Стан росту однієї спроби: власники комірок, черги материків і ціна комірок. */
    private record Grower(
            MapGrid grid,
            ContinentsDef def,
            ValueNoise noise,
            int period,
            int[] seeds,
            int[] owners,
            int[] filled,
            boolean[][] queued,
            List<PriorityQueue<Long>> frontiers) {

        void claim(int continent, int cell) {
            owners[cell] = continent;
            filled[continent]++;
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (owners[neighbor] == ContinentMap.SEA && !queued[continent][neighbor]) {
                    queued[continent][neighbor] = true;
                    frontiers.get(continent).add(cost(continent, neighbor) << CELL_BITS | neighbor);
                }
            }
        }

        /** Бере найдешевшу вільну комірку, що не торкається іншого материка; {@code false} — брати нічого. */
        boolean grow(int continent) {
            PriorityQueue<Long> frontier = frontiers.get(continent);
            while (!frontier.isEmpty()) {
                int cell = (int) (frontier.poll() & CELL_MASK);
                // Комірка поруч з іншим материком лишається морем назавжди: той материк уже не зникне.
                if (owners[cell] == ContinentMap.SEA && !touchesOther(cell, continent)) {
                    claim(continent, cell);
                    return true;
                }
            }
            return false;
        }

        boolean touchesOther(int cell, int continent) {
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                int owner = owners[neighbor];
                if (owner != ContinentMap.SEA && owner != continent) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Відстань від зерна, помножена на шум: множник — від {@code 1 − порізаність} до {@code 1 + порізаність}.
         * Множник, а не добавка, щоб затоки й півострови були в масштабі материка, хоч би який він завбільшки. Край
         * карти — після всього іншого.
         */
        long cost(int continent, int cell) {
            MapCell target = grid.cells().get(cell);
            GridPoint from = grid.cells().get(seeds[continent]).site();
            long dx = target.site().x() - from.x();
            long dy = target.site().y() - from.y();
            long distance = (long) Math.sqrt((double) (dx * dx + dy * dy));
            long centered = Math.clamp(
                    (2L * noise.sample(target.site().x(), target.site().y(), period) - ValueNoise.MAX) * NOISE_CONTRAST,
                    -ValueNoise.MAX,
                    ValueNoise.MAX);
            long factorPct = 100 + def.roughness() * centered / ValueNoise.MAX;
            long cost = distance * factorPct;
            return target.edge() ? cost + EDGE_PENALTY : cost;
        }
    }
}
