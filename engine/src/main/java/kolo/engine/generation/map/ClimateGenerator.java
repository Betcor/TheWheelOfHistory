package kolo.engine.generation.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import kolo.engine.content.ClimateDef;
import kolo.engine.content.ClimateMoistureDef;
import kolo.engine.content.ClimateTemperatureDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.WorldClimateDef;
import kolo.engine.error.Checks;
import kolo.engine.rng.Rng;
import kolo.engine.state.Climate;
import kolo.engine.state.Cover;
import kolo.engine.state.GridPoint;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Клімат суходолу (GD §3.5): температура, волога, кліматичний пояс і покрив кожної провінції.
 *
 * <ol>
 *   <li>{@link #WORLD_KIND} — колесо клімату світу за вагами контенту: зсув температури всієї карти.
 *   <li>Температура = широта (екватор — середня лінія карти, полюси — верхній і нижній краї) + зсув світу − висота ×
 *       охолодження + шум.
 *   <li>Волога = берегова волога − кроки від моря × висихання + шум.
 *   <li>Пояс і покрив — за порогами й умовами контенту; тип місцевості — покрив, якщо він є, інакше рельєф.
 * </ol>
 *
 * <p>Колесо нейтральне: сектори {@link OutcomeTier#PARTIAL}, без переваги.
 */
public final class ClimateGenerator {

    public static final WheelKind WORLD_KIND = new WheelKind("world_climate");

    /** Розтяг шуму, як у рельєфі й береговій лінії: інакше крайніх значень майже немає. */
    static final int NOISE_CONTRAST = 3;

    private ClimateGenerator() {}

    /**
     * @param rng окремий потік клімату; всередині — {@code world} на колесо, {@code temperature} і {@code moisture} на
     *     шум
     * @param grid сітка, з якої зроблено {@code continents}
     * @param relief рельєф тих самих материків
     */
    public static ClimateMap generate(
            Rng rng, ContentPack content, MapGrid grid, ContinentMap continents, ReliefMap relief) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(continents, "continents");
        Objects.requireNonNull(relief, "relief");
        Checks.inRange(
                "cells",
                continents.cellContinents().size(),
                grid.cells().size(),
                grid.cells().size());
        Checks.inRange("relief", relief.heights().size(), continents.landCells(), continents.landCells());
        ClimateDef def = content.map().climate();

        WheelSpin<WorldClimateDef> spin = Wheel.spin(
                rng.fork("world"),
                WORLD_KIND,
                worldSectors(def.worlds()),
                Advantage.NONE,
                content.balance().wheel().strength(WORLD_KIND),
                0,
                null);
        WorldClimateDef world = spin.value();

        int[] fromSea = fromSea(grid, continents);
        int period = grid.cellSide() * def.noiseCells();
        ValueNoise temperatureNoise = new ValueNoise(rng.fork("temperature").nextLong());
        ValueNoise moistureNoise = new ValueNoise(rng.fork("moisture").nextLong());
        TreeMap<Integer, Integer> temperatures = new TreeMap<>();
        TreeMap<Integer, Integer> moistures = new TreeMap<>();
        TreeMap<Integer, Climate> climates = new TreeMap<>();
        TreeMap<Integer, Cover> covers = new TreeMap<>();
        TreeMap<Integer, Terrain> terrains = new TreeMap<>();
        for (int cell = 0; cell < grid.cells().size(); cell++) {
            if (!continents.isLand(cell)) {
                continue;
            }
            GridPoint site = grid.cells().get(cell).site();
            int height = relief.heights().get(cell);
            Relief level = relief.reliefs().get(cell);
            int temperature = temperature(
                    def.temperature(),
                    latitude(site.y(), grid.height()),
                    world.temperatureShift(),
                    height,
                    temperatureNoise.signed(site.x(), site.y(), period, NOISE_CONTRAST));
            int moisture = moisture(
                    def.moisture(), fromSea[cell], moistureNoise.signed(site.x(), site.y(), period, NOISE_CONTRAST));
            Climate climate = def.climate(temperature, moisture);
            Optional<Cover> cover = def.cover(climate, level, moisture, height);
            temperatures.put(cell, temperature);
            moistures.put(cell, moisture);
            climates.put(cell, climate);
            if (cover.isPresent()) {
                covers.put(cell, cover.get());
            }
            terrains.put(cell, Terrain.of(level, cover));
        }
        return new ClimateMap(world.id(), temperatures, moistures, climates, covers, terrains, spin.record());
    }

    static List<Sector<WorldClimateDef>> worldSectors(List<WorldClimateDef> worlds) {
        List<Sector<WorldClimateDef>> sectors = new ArrayList<>();
        for (WorldClimateDef world : worlds) {
            sectors.add(new Sector<>(
                    world.id().value(), world.weight(), world, WorldSizeWheel.QUALITY, OutcomeTier.PARTIAL, List.of()));
        }
        return sectors;
    }

    /**
     * Відстань від екватора в одиницях {@link ClimateDef#MAX_VALUE}: 0 — середня лінія карти, {@code MAX_VALUE} —
     * верхній чи нижній край.
     */
    static int latitude(int y, int mapHeight) {
        Checks.inRange("map_height", mapHeight, 1, Integer.MAX_VALUE);
        long fromEquator = Math.abs(2L * y - mapHeight);
        return (int) Math.min(ClimateDef.MAX_VALUE, fromEquator * ClimateDef.MAX_VALUE / mapHeight);
    }

    /**
     * Температура: від {@code equator} до {@code pole} за широтою, + зсув світу, − висота × {@code height_cooling} /
     * 100, + шум у межах {@code ±noise_amplitude}; обрізано до {@code 0..}{@value ClimateDef#MAX_VALUE}.
     *
     * @param latitude відстань від екватора, {@code 0..}{@value ClimateDef#MAX_VALUE}
     * @param noise шум зі знаком у {@code [−ValueNoise.MAX, ValueNoise.MAX]}
     */
    static int temperature(ClimateTemperatureDef def, int latitude, int shift, int height, int noise) {
        long base = def.equator() - (long) (def.equator() - def.pole()) * latitude / ClimateDef.MAX_VALUE;
        long cooling = (long) height * def.heightCooling() / 100;
        long wobble = (long) def.noiseAmplitude() * noise / ValueNoise.MAX;
        return Math.clamp(base + shift - cooling + wobble, 0, ClimateDef.MAX_VALUE);
    }

    /**
     * Волога: {@code coast} мінус {@code inland_drying} за кожен крок углиб від берегової провінції, + шум у межах
     * {@code ±noise_amplitude}; обрізано до {@code 0..}{@value ClimateDef#MAX_VALUE}.
     *
     * @param fromSea кроків до найближчого моря, {@code ≥ 1}; {@link Integer#MAX_VALUE} — моря не видно
     */
    static int moisture(ClimateMoistureDef def, int fromSea, int noise) {
        long drying = (long) (Math.max(1, fromSea) - 1) * def.inlandDrying();
        long wobble = (long) def.noiseAmplitude() * noise / ValueNoise.MAX;
        return Math.clamp(def.coast() - drying + wobble, 0, ClimateDef.MAX_VALUE);
    }

    /** Кроків по суходолу до найближчої комірки моря: берегова — 1, море — 0; без моря — {@link Integer#MAX_VALUE}. */
    static int[] fromSea(MapGrid grid, ContinentMap continents) {
        int[] distance = new int[grid.cells().size()];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int cell = 0; cell < distance.length; cell++) {
            if (!continents.isLand(cell)) {
                distance[cell] = 0;
                queue.add(cell);
            }
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
}
