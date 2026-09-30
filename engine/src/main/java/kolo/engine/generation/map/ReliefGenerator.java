package kolo.engine.generation.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.ReliefDef;
import kolo.engine.error.Checks;
import kolo.engine.rng.Rng;
import kolo.engine.state.GridPoint;
import kolo.engine.state.Relief;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Рельєф суходолу (GD §3.5): гірські хребти материків, висота кожної провінції й рівень рельєфу за порогами контенту.
 *
 * <ol>
 *   <li>{@link #RIDGES_KIND} — для кожного материка колесо кількості хребтів: рівні сектори {@code ridges_<n>} у
 *       діапазоні контенту, але не більше одного хребта на {@code ridge_min_provinces} провінцій материка.
 *   <li>Хребет — ламана від випадкової внутрішньої комірки материка у два протилежні боки: щоразу до сусіда,
 *       найближчого до напрямку, а напрямок звертає на випадковий кут до {@code ridge_wander}. Половина обривається
 *       біля моря або коли жоден сусід не лежить у межах {@value #MAX_TURN_DEGREES}° від напрямку; недобрані кроки
 *       першої половини переходять до другої. Довжина — частка поперечника материка (кореня з кількості провінцій).
 *   <li>Висота = основа + внесок найближчого хребта (спадає з кроками від нього) + шум, обрізано до {@code 0..100}.
 * </ol>
 *
 * <p>Колеса нейтральні: сектори {@link OutcomeTier#PARTIAL}, без переваги.
 */
public final class ReliefGenerator {

    public static final WheelKind RIDGES_KIND = new WheelKind("world_ridge_count");

    /** Сусід, що відхиляється від напрямку більше, — уже злам хребта, а не вигин. */
    static final int MAX_TURN_DEGREES = 60;

    /** Розтяг шуму, як у береговій лінії материків: інакше крайніх значень майже немає. */
    static final int NOISE_CONTRAST = 3;

    private static final double MIN_COS = StrictMath.cos(StrictMath.toRadians(MAX_TURN_DEGREES));

    /** Одиничні вектори кожного цілого кута: {@link StrictMath} однаковий на всіх платформах. */
    private static final double[] COS = new double[360];

    private static final double[] SIN = new double[360];

    static {
        for (int degrees = 0; degrees < 360; degrees++) {
            COS[degrees] = StrictMath.cos(StrictMath.toRadians(degrees));
            SIN[degrees] = StrictMath.sin(StrictMath.toRadians(degrees));
        }
    }

    private ReliefGenerator() {}

    /**
     * @param rng окремий потік рельєфу; всередині — {@code ridges:<материк>} на колесо кількості хребтів,
     *     {@code ridge:<материк>:<n>} на кожен хребет ({@code start}, {@code angle}, {@code walk}) і {@code noise}
     * @param grid сітка, з якої зроблено {@code continents}
     */
    public static ReliefMap generate(Rng rng, ContentPack content, MapGrid grid, ContinentMap continents) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(continents, "continents");
        Checks.inRange(
                "cells",
                continents.cellContinents().size(),
                grid.cells().size(),
                grid.cells().size());
        ReliefDef def = content.map().relief();
        int strength = content.balance().wheel().strength(RIDGES_KIND);

        List<RollRecord> rolls = new ArrayList<>();
        List<Ridge> ridges = new ArrayList<>();
        for (int c = 0; c < continents.continents().size(); c++) {
            List<Integer> cells = continents.continents().get(c).cells();
            WheelSpin<Integer> spin = Wheel.spin(
                    rng.fork("ridges:" + c),
                    RIDGES_KIND,
                    WorldSizeWheel.rangeSectors("ridges_", ridgeRange(def, cells.size())),
                    Advantage.NONE,
                    strength,
                    0,
                    null);
            rolls.add(spin.record());
            int length = ridgeLength(def, cells.size());
            for (int n = 0; n < spin.value(); n++) {
                ridges.add(ridge(rng.fork("ridge:" + c + ":" + n), grid, continents, c, length, def.ridgeWander()));
            }
        }

        int[] fromRidge = distances(grid, continents, ridges);
        ValueNoise noise = new ValueNoise(rng.fork("noise").nextLong());
        int period = grid.cellSide() * def.noiseCells();
        TreeMap<Integer, Integer> heights = new TreeMap<>();
        TreeMap<Integer, Relief> reliefs = new TreeMap<>();
        for (int cell = 0; cell < grid.cells().size(); cell++) {
            if (!continents.isLand(cell)) {
                continue;
            }
            GridPoint site = grid.cells().get(cell).site();
            int height = height(def, fromRidge[cell], noise.signed(site.x(), site.y(), period, NOISE_CONTRAST));
            heights.put(cell, height);
            reliefs.put(cell, def.relief(height));
        }
        return new ReliefMap(heights, reliefs, ridges, rolls);
    }

    /** Діапазон колеса хребтів материка: контент, обрізаний до одного хребта на {@code ridge_min_provinces}. */
    static CountRange ridgeRange(ReliefDef def, int provinces) {
        int cap = provinces / def.ridgeMinProvinces();
        return new CountRange(
                Math.min(def.ridges().min(), cap), Math.min(def.ridges().max(), cap));
    }

    /** Довжина хребта в комірках: частка кореня з кількості провінцій материка, щонайменше одна. */
    static int ridgeLength(ReliefDef def, int provinces) {
        return Math.max(1, (int) ((long) isqrt(provinces) * def.ridgeLengthPct() / 100));
    }

    /**
     * Висота: основа + внесок хребта, що спадає на {@code ridge_falloff} за крок, + шум у межах
     * {@code ±noise_amplitude}; обрізано до {@code 0..}{@value ReliefDef#MAX_HEIGHT}.
     *
     * @param fromRidge кроків до найближчого хребта; {@link Integer#MAX_VALUE} — хребтів на материку немає
     * @param noise шум зі знаком у {@code [−ValueNoise.MAX, ValueNoise.MAX]}
     */
    static int height(ReliefDef def, int fromRidge, int noise) {
        long ridge = Math.max(0, def.ridgeHeight() - (long) fromRidge * def.ridgeFalloff());
        long wobble = (long) def.noiseAmplitude() * noise / ValueNoise.MAX;
        return Math.clamp(def.baseHeight() + ridge + wobble, 0, ReliefDef.MAX_HEIGHT);
    }

    /**
     * Хребет від випадкової внутрішньої комірки материка: половина довжини в випадковому напрямку, решта — в
     * протилежному.
     */
    private static Ridge ridge(Rng rng, MapGrid grid, ContinentMap continents, int continent, int length, int wander) {
        List<Integer> candidates = interior(grid, continents, continent);
        int start = candidates.get(rng.fork("start").nextInt(candidates.size()));
        int angle = rng.fork("angle").nextInt(360);
        Rng walk = rng.fork("walk");
        boolean[] taken = new boolean[grid.cells().size()];
        taken[start] = true;
        List<Integer> forward = walk(walk, grid, continents, continent, start, angle, length / 2, wander, taken);
        // Якщо перша половина вперлася в берег, решту довжини добирає друга.
        int backwardSteps = length - 1 - forward.size();
        List<Integer> backward =
                walk(walk, grid, continents, continent, start, (angle + 180) % 360, backwardSteps, wander, taken);
        List<Integer> cells = new ArrayList<>(backward);
        Collections.reverse(cells);
        cells.add(start);
        cells.addAll(forward);
        return new Ridge(continent, cells);
    }

    /** Комірки материка, що не торкаються моря; якщо таких немає — усі комірки материка. */
    private static List<Integer> interior(MapGrid grid, ContinentMap continents, int continent) {
        List<Integer> cells = continents.continents().get(continent).cells();
        List<Integer> inner = new ArrayList<>();
        for (int cell : cells) {
            boolean coastal = false;
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                coastal |= !continents.isLand(neighbor);
            }
            if (!coastal) {
                inner.add(cell);
            }
        }
        return inner.isEmpty() ? cells : inner;
    }

    /**
     * До {@code steps} кроків від {@code from}: щоразу до сусіда того самого материка, ще не взятого хребтом, чий
     * напрямок найближчий до {@code angle} (при рівності — менший номер); після кроку напрямок звертає на випадковий
     * кут у межах {@code ±wander}.
     */
    private static List<Integer> walk(
            Rng rng,
            MapGrid grid,
            ContinentMap continents,
            int continent,
            int from,
            int angle,
            int steps,
            int wander,
            boolean[] taken) {
        List<Integer> path = new ArrayList<>();
        int current = from;
        int direction = angle;
        for (int step = 0; step < steps; step++) {
            GridPoint here = grid.cells().get(current).site();
            int best = -1;
            double bestCos = 0;
            for (int neighbor : grid.cells().get(current).neighbors()) {
                if (taken[neighbor] || continents.cellContinents().get(neighbor) != continent) {
                    continue;
                }
                GridPoint there = grid.cells().get(neighbor).site();
                double dx = there.x() - here.x();
                double dy = there.y() - here.y();
                double cos = (dx * COS[direction] + dy * SIN[direction]) / Math.sqrt(dx * dx + dy * dy);
                if (cos >= MIN_COS && (best < 0 || cos > bestCos)) {
                    best = neighbor;
                    bestCos = cos;
                }
            }
            if (best < 0) {
                break;
            }
            taken[best] = true;
            path.add(best);
            current = best;
            direction = Math.floorMod(direction + rng.nextInt(2 * wander + 1) - wander, 360);
        }
        return path;
    }

    /** Кроків по суходолу до найближчої комірки хребта; без хребта на материку — {@link Integer#MAX_VALUE}. */
    static int[] distances(MapGrid grid, ContinentMap continents, List<Ridge> ridges) {
        int[] distance = new int[grid.cells().size()];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (Ridge ridge : ridges) {
            for (int cell : ridge.cells()) {
                if (distance[cell] != 0) {
                    distance[cell] = 0;
                    queue.add(cell);
                }
            }
        }
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (continents.isLand(neighbor) && distance[neighbor] == Integer.MAX_VALUE) {
                    distance[neighbor] = distance[cell] + 1;
                    queue.add(neighbor);
                }
            }
        }
        return distance;
    }

    /** Цілий корінь, округлений вниз. */
    static int isqrt(int value) {
        int root = (int) Math.sqrt(value);
        while ((long) root * root > value) {
            root--;
        }
        while ((long) (root + 1) * (root + 1) <= value) {
            root++;
        }
        return root;
    }
}
