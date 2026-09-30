package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.generation.map.PlacementGenerator;
import kolo.engine.generation.map.PlacementMap;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Розміщення держав на вбудованому контенті: у кожному шаблоні й розмірі світу держави зв'язні, стоять на своїх
 * материках і майже завжди отримують рівно свої цілі, а разом займають суходіл без частки нічийних земель; більший
 * рівень площі дає більшу державу; найбільший світ вкладається в бюджет генерації карти.
 */
class BundledPlacementIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void everyTemplateAndWorldSizePlacesAllCountries() {
        long countries = 0;
        long exact = 0;
        long claimableTotal = 0;
        long claimedTotal = 0;
        TreeMap<AreaLevelId, long[]> byArea = new TreeMap<>();
        for (MapTemplateDef template : PACK.map().templates()) {
            for (long seed = 0; seed < 25; seed++) {
                int players = 1 + (int) (seed % WorldLimits.MAX_PLAYERS);
                NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
                World world = world(seed, players, share, template);

                PlacementMap placement = PlacementGenerator.generate(
                        Rng.of(seed).fork("placement"), PACK, world.size(), world.grid(), world.continents());

                assertValid(world, placement, PACK.map().placement().minProvinces());
                long targets = 0;
                for (PlacedCountry country : placement.countries()) {
                    countries++;
                    exact += country.provinces() == country.target() ? 1 : 0;
                    targets += country.target();
                    // Відхилення рідкісні: вільна ділянка скінчилася раніше, недобір дістався наступній державі.
                    assertThat(country.provinces()).isBetween(country.target() / 2, country.target() * 3 / 2);
                    long[] sum = byArea.computeIfAbsent(country.area(), id -> new long[2]);
                    sum[0] += country.provinces();
                    sum[1]++;
                }
                long occupied = placement.countries().stream()
                        .mapToInt(PlacedCountry::continent)
                        .distinct()
                        .count();
                long claimable = claimable(world.continents(), world.size().unclaimedBp());
                if (occupied == world.continents().continents().size()) {
                    // Кожен материк має держави: разом вони мають зайняти весь суходіл без нічийної частки.
                    assertThat(targets).isEqualTo(claimable);
                } else {
                    assertThat(targets).isLessThanOrEqualTo(claimable);
                }
                assertThat((long) placement.claimedCells()).isLessThanOrEqualTo(targets);
                claimableTotal += targets;
                claimedTotal += placement.claimedCells();
            }
        }
        assertThat(exact * 100).isGreaterThanOrEqualTo(countries * 97);
        assertThat(claimedTotal * 1000).isGreaterThanOrEqualTo(claimableTotal * 998);
        // Більший рівень площі — більша держава в середньому.
        long previous = 0;
        for (AreaLevelDef area : PACK.map().placement().areas()) {
            long[] sum = byArea.get(area.id());
            assertThat(sum).as("рівень %s випадав", area.id()).isNotNull();
            long mean = sum[0] / sum[1];
            assertThat(mean).as("площа %s", area.id()).isGreaterThan(previous);
            previous = mean;
        }
    }

    @Test
    @Tag("budget")
    void largestWorldPlacementFitsBudget() {
        MapTemplateDef template = PACK.map().templates().stream()
                .min((a, b) -> Integer.compare(a.landPct(), b.landPct()))
                .orElseThrow();
        int provinces = PACK.balance().world().provinces().max();
        WorldSize size = new WorldSize(
                WorldLimits.MAX_PLAYERS,
                WorldLimits.MAX_COUNTRIES - WorldLimits.MAX_PLAYERS,
                template.id(),
                template.continents().max(),
                100,
                provinces,
                500,
                List.of());
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), PACK.map().grid(), template.gridCells(provinces));
        ContinentMap continents = ContinentGenerator.generate(Rng.of(1), PACK, size, grid);

        Budget.Timed<PlacementMap> timed =
                Budget.best(() -> PlacementGenerator.generate(Rng.of(1), PACK, size, grid, continents));

        assertThat(timed.result().countries()).hasSize(WorldLimits.MAX_COUNTRIES);
        // Бюджет усієї генерації карти — 2 с; розміщення — лише її частина.
        assertThat(timed.millis()).isLessThan(500);
    }

    /** Держав стільки, скільки дав розмір світу; кожна — зв'язна, на своєму материку, не менша за мінімум. */
    private static void assertValid(World world, PlacementMap placement, int minProvinces) {
        MapGrid grid = world.grid();
        assertThat(placement.cellCountries()).hasSize(grid.cells().size());
        assertThat(placement.countries()).hasSize(world.size().countries());
        for (int i = 0; i < placement.countries().size(); i++) {
            PlacedCountry country = placement.countries().get(i);
            assertThat(country.provinces()).isGreaterThanOrEqualTo(minProvinces);
            for (int cell : country.cells()) {
                assertThat(world.continents().cellContinents().get(cell)).isEqualTo(country.continent());
            }
            boolean[] seen = new boolean[grid.cells().size()];
            ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(country.seed()));
            seen[country.seed()] = true;
            int reached = 0;
            while (!queue.isEmpty()) {
                int cell = queue.poll();
                reached++;
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    if (placement.country(neighbor) == i && !seen[neighbor]) {
                        seen[neighbor] = true;
                        queue.add(neighbor);
                    }
                }
            }
            assertThat(reached).isEqualTo(country.provinces());
        }
    }

    static long claimable(ContinentMap continents, int unclaimedBp) {
        return continents.continents().stream()
                .mapToLong(continent -> (long) continent.cells().size() * (10_000 - unclaimedBp) / 10_000)
                .sum();
    }

    static World world(long seed, int players, NpcShare share, MapTemplateDef template) {
        Rng rng = Rng.of(seed);
        WorldSize size = WorldSizeWheel.generate(
                rng.fork("world_size"),
                PACK,
                new WorldSizeInput(players, share, Optional.of(template.id()), OptionalInt.empty()));
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), PACK.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(rng.fork("continents"), PACK, size, grid);
        return new World(size, grid, continents);
    }

    record World(WorldSize size, MapGrid grid, ContinentMap continents) {}
}
