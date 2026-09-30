package kolo.engine.generation.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.PlacementDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.util.Fixed;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Розміщення держав на карті (GD §3.6, колеса 1–2 GD §4.1, ADR 0033).
 *
 * <ol>
 *   <li>Кожна держава по черзі — спершу гравці, потім NPC — крутить колесо {@link #CONTINENT_KIND} (сектори
 *       {@code continent_<n>}, вага — вільний простір материка: його частка суходолу для держав мінус площі, які вже
 *       обрали держави перед нею) і колесо {@link #AREA_KIND} (рівні площі контенту, площа — у відсотках середньої
 *       держави). Материк, де не вистачить мінімуму провінцій ще на одну державу, на колесо не потрапляє.
 *   <li>Площі масштабуються (рішення автора): кожна держава отримує мінімум, решта суходолу для держав ділиться
 *       пропорційно площам із коліс, але материк не віддає більше за свою частку — надлишок переходить до держав на
 *       інших материках. Частка суходолу материка для держав — {@code його провінцій × (1 − нічийні)} вниз, тож нічийні
 *       землі є на кожному материку; материк без держав лишається нічийним цілком.
 *   <li>Держави стають на материк по черзі (GD §3.6): спершу гравці в порядку генерації, потім NPC від найбільшої
 *       цілі до найменшої. Зерно — у найвільнішому місці (рішення автора): рівноймовірно серед вільних комірок, чий
 *       простір (відстань у кроках до зайнятої землі або подвоєна — до берега) не менший за {@value #SEED_SPREAD_PCT}%
 *       найбільшого, лише у вільній ділянці, що вміщує ціль (або в найбільшій, якщо такої немає).
 *   <li>Ріст: держава бере найдешевшу вільну комірку поруч зі своєю територією, доки не набере ціль. Ціна — відстань
 *       від зерна, помножена на шум ({@code 1 ± порізаність}).
 * </ol>
 *
 * <p>Якщо вільна ділянка скінчилася раніше за ціль, недобір переходить до наступної держави в черзі материка. Розкладка
 * повторюється з новими зернами й шумом (до {@value #MAX_ATTEMPTS} спроб), доки кожна держава не отримає рівно свою
 * ціль; інакше береться спроба з найменшим відхиленням, а недобір останньої держави лишається нічийною землею.
 * Колеса не мають переваги й сектори {@link OutcomeTier#PARTIAL}: материк нейтральний (якість {@value #QUALITY}),
 * якість площі — з контенту.
 */
public final class PlacementGenerator {

    public static final WheelKind CONTINENT_KIND = new WheelKind("generation_continent");
    public static final WheelKind AREA_KIND = new WheelKind("generation_area");

    /** Материк — лише місце: нейтральний для стріків. */
    static final int QUALITY = 50;

    /** Зерно держави — серед комірок, чий простір не менший за цю частку найбільшого. */
    static final int SEED_SPREAD_PCT = 75;

    /** Спроб розкладки материка: зазвичай вистачає першої, повтор — запас для тісних материків. */
    static final int MAX_ATTEMPTS = 20;

    /** Як у материків: розтяг середнього трьох октав шуму на весь діапазон порізаності. */
    static final int NOISE_CONTRAST = 3;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    /** Комірка в записі черги — молодші біти; {@code MapGridDef.MAX_CELLS} менший. */
    private static final int CELL_BITS = 15;

    private static final long CELL_MASK = (1L << CELL_BITS) - 1;

    private PlacementGenerator() {}

    /**
     * @param rng окремий потік розміщення; всередині — {@code country:<n>} → {@code continent}/{@code area} на колеса
     *     держави й {@code layout:<материк>:<спроба>} → {@code seeds}/{@code noise} на розкладку материка
     * @param size розмір світу: скільки держав (гравці першими) і частка нічийних земель
     * @param continents материки цієї сітки
     * @throws ValidationException якщо материки не від цієї сітки ({@link ErrorCode#VALUE_OUT_OF_RANGE})
     * @throws InvariantViolationException якщо жоден материк не вміщує мінімум ще на одну державу (контент це
     *     виключає)
     */
    public static PlacementMap generate(
            Rng rng, ContentPack content, WorldSize size, MapGrid grid, ContinentMap continents) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(continents, "continents");
        if (continents.cellContinents().size() != grid.cells().size()) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE,
                    ErrorDetails.of(
                            "field",
                            "continents",
                            "value",
                            continents.cellContinents().size(),
                            "expected",
                            grid.cells().size()));
        }
        PlacementDef def = content.map().placement();
        int countries = size.countries();
        int[] capacities = capacities(continents, size.unclaimedBp());

        int[] countryContinents = new int[countries];
        AreaLevelDef[] areas = new AreaLevelDef[countries];
        List<List<RollRecord>> rolls = new ArrayList<>();
        int[] counts = new int[capacities.length];
        long[] shares = new long[capacities.length];
        for (int i = 0; i < countries; i++) {
            Rng countryRng = rng.fork("country:" + i);
            List<Sector<Integer>> sectors = continentSectors(capacities, counts, shares, countries, def.minProvinces());
            if (sectors.isEmpty()) {
                throw new InvariantViolationException(
                        ErrorDetails.of("field", "placement", "value", i, "countries", countries));
            }
            WheelSpin<Integer> continent = spin(content, countryRng.fork("continent"), CONTINENT_KIND, sectors);
            WheelSpin<AreaLevelDef> area = spin(content, countryRng.fork("area"), AREA_KIND, areaSectors(def));
            int c = continent.value();
            countryContinents[i] = c;
            areas[i] = area.value();
            counts[c]++;
            shares[c] += area.value().sharePct();
            rolls.add(List.of(continent.record(), area.record()));
        }

        int[] targets = targets(capacities, countryContinents, areas, def.minProvinces());
        int[] owners = new int[grid.cells().size()];
        Arrays.fill(owners, PlacementMap.NONE);
        int[] seeds = new int[countries];
        for (int c = 0; c < capacities.length; c++) {
            List<Integer> members = new ArrayList<>();
            for (int i = 0; i < countries; i++) {
                if (countryContinents[i] == c) {
                    members.add(i);
                }
            }
            if (!members.isEmpty()) {
                Layout layout = bestLayout(rng, grid, continents, def, c, members, targets, size.players());
                for (int m = 0; m < members.size(); m++) {
                    seeds[members.get(m)] = layout.seeds()[m];
                }
                for (int cell : continents.continents().get(c).cells()) {
                    int owner = layout.owners()[cell];
                    owners[cell] = owner == PlacementMap.NONE ? PlacementMap.NONE : members.get(owner);
                }
            }
        }
        return result(countryContinents, areas, targets, seeds, owners, rolls);
    }

    /** Частка суходолу кожного материка для держав: провінцій × (1 − нічийні), вниз. */
    static int[] capacities(ContinentMap continents, int unclaimedBp) {
        int[] capacities = new int[continents.continents().size()];
        for (int c = 0; c < capacities.length; c++) {
            long land = continents.continents().get(c).cells().size();
            capacities[c] = (int) Fixed.mulDiv(land, Wheel.TOTAL_BP - unclaimedBp, Wheel.TOTAL_BP);
        }
        return capacities;
    }

    /**
     * Сектори материків, що вміщують ще одну державу з мінімумом. Вага — вільний простір у одиницях «відсоток
     * середньої держави»: {@code частка × держав × 100 − суходіл для держав × обрані площі}, щоб не округлювати
     * середню. Якщо вільного простору не лишилося ніде, вага — частка материка. Ваги переводяться в частки
     * {@link Wheel#TOTAL_BP} вниз, щонайменше 1.
     */
    static List<Sector<Integer>> continentSectors(
            int[] capacities, int[] counts, long[] shares, int countries, int minimum) {
        long total = Arrays.stream(capacities).asLongStream().sum();
        boolean[] eligible = new boolean[capacities.length];
        long[] free = new long[capacities.length];
        boolean anyFree = false;
        for (int c = 0; c < capacities.length; c++) {
            eligible[c] = (long) (counts[c] + 1) * minimum <= capacities[c];
            free[c] = (long) capacities[c] * countries * 100 - total * shares[c];
            anyFree |= eligible[c] && free[c] > 0;
        }
        long[] weights = new long[capacities.length];
        long sum = 0;
        for (int c = 0; c < capacities.length; c++) {
            if (eligible[c]) {
                weights[c] = anyFree ? Math.max(0, free[c]) : capacities[c];
            }
            sum += weights[c];
        }
        List<Sector<Integer>> sectors = new ArrayList<>();
        for (int c = 0; c < capacities.length; c++) {
            if (weights[c] > 0) {
                int weight = (int) Math.max(1, Fixed.mulDiv(weights[c], Wheel.TOTAL_BP, sum));
                sectors.add(new Sector<>("continent_" + c, weight, c, QUALITY, OutcomeTier.PARTIAL, List.of()));
            }
        }
        return sectors;
    }

    static List<Sector<AreaLevelDef>> areaSectors(PlacementDef def) {
        List<Sector<AreaLevelDef>> sectors = new ArrayList<>();
        for (AreaLevelDef area : def.areas()) {
            sectors.add(new Sector<>(
                    area.id().value(), area.weight(), area, area.quality(), OutcomeTier.PARTIAL, area.tags()));
        }
        return sectors;
    }

    /**
     * Ціль кожної держави: мінімум плюс частка решти суходолу для держав, пропорційна площі з колеса. Материк не
     * віддає більше за свою частку: заповнені материки виходять із поділу, а решта ділиться між іншими (рівень
     * «води» піднімається). Округлення — найбільшими залишками, при рівності — менший номер.
     */
    static int[] targets(int[] capacities, int[] countryContinents, AreaLevelDef[] areas, int minimum) {
        int continents = capacities.length;
        long[] demand = new long[continents];
        long[] room = new long[continents];
        int[] counts = new int[continents];
        for (int i = 0; i < countryContinents.length; i++) {
            counts[countryContinents[i]]++;
            demand[countryContinents[i]] += areas[i].sharePct();
        }
        long rest = Arrays.stream(capacities).asLongStream().sum();
        for (int c = 0; c < continents; c++) {
            room[c] = capacities[c] - (long) counts[c] * minimum;
            rest -= (long) counts[c] * minimum;
        }

        long[] extra = new long[continents];
        boolean[] open = new boolean[continents];
        for (int c = 0; c < continents; c++) {
            open[c] = counts[c] > 0;
        }
        while (true) {
            long openDemand = 0;
            for (int c = 0; c < continents; c++) {
                if (open[c]) {
                    openDemand += demand[c];
                }
            }
            if (openDemand == 0) {
                break;
            }
            // Материк, що заповнюється за поточного рівня, заповниться й за вищого: знімається одразу.
            long level = rest;
            boolean saturated = false;
            for (int c = 0; c < continents; c++) {
                if (open[c] && Fixed.mulDiv(level, demand[c], openDemand) >= room[c]) {
                    extra[c] = room[c];
                    rest -= room[c];
                    open[c] = false;
                    saturated = true;
                }
            }
            if (!saturated) {
                long[] weights = new long[continents];
                for (int c = 0; c < continents; c++) {
                    weights[c] = open[c] ? demand[c] : 0;
                }
                long[] shares = Fixed.distribute(rest, weights);
                for (int c = 0; c < continents; c++) {
                    if (open[c]) {
                        extra[c] = shares[c];
                    }
                }
                break;
            }
        }

        int[] targets = new int[countryContinents.length];
        for (int c = 0; c < continents; c++) {
            if (counts[c] == 0) {
                continue;
            }
            long[] weights = new long[countryContinents.length];
            for (int i = 0; i < countryContinents.length; i++) {
                weights[i] = countryContinents[i] == c ? areas[i].sharePct() : 0;
            }
            long[] shares = Fixed.distribute(extra[c], weights);
            for (int i = 0; i < countryContinents.length; i++) {
                if (countryContinents[i] == c) {
                    targets[i] = (int) (minimum + shares[i]);
                }
            }
        }
        return targets;
    }

    /** Перша розкладка материка, де кожна держава отримала рівно свою ціль, або з найменшим відхиленням. */
    private static Layout bestLayout(
            Rng rng,
            MapGrid grid,
            ContinentMap continents,
            PlacementDef def,
            int continent,
            List<Integer> members,
            int[] targets,
            int players) {
        int[] memberTargets =
                members.stream().mapToInt(member -> targets[member]).toArray();
        int[] order = order(members, memberTargets, players);
        boolean[] land = new boolean[grid.cells().size()];
        for (int cell : continents.continents().get(continent).cells()) {
            land[cell] = true;
        }
        int[] fromCoast = distances(grid, land, coast(grid, land));
        Layout best = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Layout layout = layout(
                    rng.fork("layout:" + continent + ":" + attempt), grid, def, land, fromCoast, memberTargets, order);
            if (best == null || layout.deviation() < best.deviation()) {
                best = layout;
            }
            if (best.deviation() == 0) {
                break;
            }
        }
        return best;
    }

    /**
     * Черга розміщення на материку (місцеві номери): спершу гравці в порядку генерації (GD §3.6), потім NPC від
     * найбільшої цілі до найменшої — дрібні держави легше вміщуються в залишки простору; при рівності — менший номер.
     */
    static int[] order(List<Integer> members, int[] targets, int players) {
        List<Integer> local = new ArrayList<>();
        for (int m = 0; m < members.size(); m++) {
            local.add(m);
        }
        local.sort((a, b) -> {
            boolean playerA = members.get(a) < players;
            boolean playerB = members.get(b) < players;
            if (playerA != playerB) {
                return playerA ? -1 : 1;
            }
            if (!playerA && targets[a] != targets[b]) {
                return Integer.compare(targets[b], targets[a]);
            }
            return Integer.compare(a, b);
        });
        return local.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Одна спроба на материку: держави по черзі стають у найвільніше місце й ростуть до цілі. Номери держав — місцеві.
     * Якщо вільна ділянка скінчилася раніше, недобір переходить до наступної держави в черзі: суходіл для держав не
     * лишається нічийним лише через те, що вільна земля опинилася не там.
     */
    private static Layout layout(
            Rng rng, MapGrid grid, PlacementDef def, boolean[] land, int[] fromCoast, int[] targets, int[] order) {
        int cells = grid.cells().size();
        Rng seedRng = rng.fork("seeds");
        ValueNoise noise = new ValueNoise(rng.fork("noise").nextLong());
        int period = grid.cellSide() * def.noiseCells();
        int[] owners = new int[cells];
        Arrays.fill(owners, PlacementMap.NONE);
        int[] seeds = new int[targets.length];
        int[] filled = new int[targets.length];
        List<Integer> claimed = new ArrayList<>();
        int carry = 0;
        for (int country : order) {
            int goal = targets[country] + carry;
            int seed = seed(seedRng, grid, land, owners, fromCoast, claimed, goal);
            seeds[country] = seed;
            filled[country] = grow(grid, def, noise, period, land, owners, country, seed, goal, claimed);
            carry = goal - filled[country];
        }
        long deviation = 0;
        for (int i = 0; i < targets.length; i++) {
            deviation += Math.abs(targets[i] - filled[i]);
        }
        return new Layout(seeds, owners, deviation);
    }

    /**
     * Зерно держави в найвільнішому місці (рішення автора). Простір — відстань у кроках до вже зайнятої землі або
     * подвоєна (+1) — до берега: біля берега сусід лише з одного боку, а на крихітній ділянці зерно все одно
     * знайдеться. Зерно — лише у вільній ділянці, що вміщує ціль, а якщо такої немає — у найбільшій; рівноймовірно серед
     * комірок, чий простір не менший за {@value #SEED_SPREAD_PCT}% найбільшого.
     */
    static int seed(
            Rng rng, MapGrid grid, boolean[] land, int[] owners, int[] fromCoast, List<Integer> claimed, int goal) {
        int cells = grid.cells().size();
        boolean[] free = new boolean[cells];
        for (int cell = 0; cell < cells; cell++) {
            free[cell] = land[cell] && owners[cell] == PlacementMap.NONE;
        }
        int[] component = new int[cells];
        List<Integer> sizes = components(grid, free, component);
        // Недосяжно: цілі разом менші за суходіл материка.
        if (sizes.isEmpty()) {
            throw new InvariantViolationException(ErrorDetails.of("field", "placement.free_land", "value", 0));
        }
        int largest = sizes.stream().mapToInt(Integer::intValue).max().orElseThrow();
        int fits = Math.min(goal, largest);
        int[] fromClaimed = distances(
                grid, land, claimed.stream().mapToInt(Integer::intValue).toArray());
        int[] room = new int[cells];
        int widest = 0;
        for (int cell = 0; cell < cells; cell++) {
            if (free[cell] && sizes.get(component[cell]) >= fits) {
                room[cell] = (int) Math.min(fromClaimed[cell], 2L * fromCoast[cell] + 1);
                widest = Math.max(widest, room[cell]);
            }
        }
        int threshold = Math.max(1, Math.ceilDiv(widest * SEED_SPREAD_PCT, 100));
        List<Integer> candidates = new ArrayList<>();
        for (int cell = 0; cell < cells; cell++) {
            if (free[cell] && sizes.get(component[cell]) >= fits && room[cell] >= threshold) {
                candidates.add(cell);
            }
        }
        return candidates.get(rng.nextInt(candidates.size()));
    }

    /**
     * Ріст держави від зерна по вільній землі: щоразу найдешевша комірка черги, доки не набере {@code goal} або
     * ділянка не скінчиться. Ціна — відстань від зерна, помножена на шум ({@code 1 ± порізаність}).
     *
     * @return скільки комірок набрала держава
     */
    private static int grow(
            MapGrid grid,
            PlacementDef def,
            ValueNoise noise,
            int period,
            boolean[] land,
            int[] owners,
            int country,
            int seed,
            int goal,
            List<Integer> claimed) {
        GridPoint from = grid.cells().get(seed).site();
        boolean[] queued = new boolean[owners.length];
        PriorityQueue<Long> frontier = new PriorityQueue<>();
        queued[seed] = true;
        frontier.add((long) seed);
        int filled = 0;
        while (filled < goal && !frontier.isEmpty()) {
            int cell = (int) (frontier.poll() & CELL_MASK);
            owners[cell] = country;
            claimed.add(cell);
            filled++;
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (land[neighbor] && owners[neighbor] == PlacementMap.NONE && !queued[neighbor]) {
                    queued[neighbor] = true;
                    frontier.add(cost(grid, def, noise, period, from, neighbor) << CELL_BITS | neighbor);
                }
            }
        }
        return filled;
    }

    /** Відстань від зерна, помножена на шум: множник — від {@code 1 − порізаність} до {@code 1 + порізаність}. */
    private static long cost(MapGrid grid, PlacementDef def, ValueNoise noise, int period, GridPoint from, int cell) {
        GridPoint target = grid.cells().get(cell).site();
        long dx = target.x() - from.x();
        long dy = target.y() - from.y();
        long distance = (long) Math.sqrt((double) (dx * dx + dy * dy));
        long centered = noise.signed(target.x(), target.y(), period, NOISE_CONTRAST);
        return distance * (100 + def.roughness() * centered / ValueNoise.MAX);
    }

    /** Зв'язні ділянки {@code mask}: номер ділянки кожної комірки в {@code component} і розміри ділянок. */
    static List<Integer> components(MapGrid grid, boolean[] mask, int[] component) {
        Arrays.fill(component, -1);
        List<Integer> sizes = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || component[start] >= 0) {
                continue;
            }
            int id = sizes.size();
            int size = 0;
            component[start] = id;
            queue.add(start);
            while (!queue.isEmpty()) {
                int cell = queue.poll();
                size++;
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    if (mask[neighbor] && component[neighbor] < 0) {
                        component[neighbor] = id;
                        queue.add(neighbor);
                    }
                }
            }
            sizes.add(size);
        }
        return sizes;
    }

    /** Берег материка: комірки, що межують з водою чи іншою землею або стоять на краю карти. */
    static int[] coast(MapGrid grid, boolean[] land) {
        List<Integer> coast = new ArrayList<>();
        for (int cell = 0; cell < land.length; cell++) {
            if (land[cell] && coastal(grid, land, cell)) {
                coast.add(cell);
            }
        }
        return coast.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean coastal(MapGrid grid, boolean[] land, int cell) {
        MapCell mapCell = grid.cells().get(cell);
        if (mapCell.edge()) {
            return true;
        }
        for (int neighbor : mapCell.neighbors()) {
            if (!land[neighbor]) {
                return true;
            }
        }
        return false;
    }

    /** Відстань у кроках по суходолу материка від найближчого джерела; недосяжні — {@link Integer#MAX_VALUE}. */
    static int[] distances(MapGrid grid, boolean[] land, int[] sources) {
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
                if (land[neighbor] && distance[neighbor] == Integer.MAX_VALUE) {
                    distance[neighbor] = distance[cell] + 1;
                    queue.add(neighbor);
                }
            }
        }
        return distance;
    }

    private static PlacementMap result(
            int[] countryContinents,
            AreaLevelDef[] areas,
            int[] targets,
            int[] seeds,
            int[] owners,
            List<List<RollRecord>> rolls) {
        int countries = countryContinents.length;
        List<List<Integer>> cells = new ArrayList<>();
        for (int i = 0; i < countries; i++) {
            cells.add(new ArrayList<>());
        }
        List<Integer> cellCountries = new ArrayList<>(owners.length);
        for (int cell = 0; cell < owners.length; cell++) {
            cellCountries.add(owners[cell]);
            if (owners[cell] != PlacementMap.NONE) {
                cells.get(owners[cell]).add(cell);
            }
        }
        List<PlacedCountry> placed = new ArrayList<>(countries);
        for (int i = 0; i < countries; i++) {
            placed.add(new PlacedCountry(
                    countryContinents[i],
                    areas[i].id(),
                    areas[i].tags(),
                    targets[i],
                    seeds[i],
                    cells.get(i),
                    rolls.get(i)));
        }
        return new PlacementMap(placed, cellCountries);
    }

    private static <T> WheelSpin<T> spin(ContentPack content, Rng rng, WheelKind kind, List<Sector<T>> sectors) {
        int strength = content.balance().wheel().strength(kind);
        return Wheel.spin(rng, kind, sectors, Advantage.NONE, strength, TURN, null);
    }

    /**
     * Спроба розкладки материка: зерна, власники (місцеві номери) і відхилення — сума різниць між цілями й
     * отриманими площами.
     */
    private record Layout(int[] seeds, int[] owners, long deviation) {}
}
