package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MedianRange;
import kolo.engine.content.PowerComponent;
import kolo.engine.generation.country.CountryGenerationInput;
import kolo.engine.generation.country.CountryGenerator;
import kolo.engine.generation.country.PopulationWheel;
import kolo.engine.generation.country.PowerBudget;
import kolo.engine.generation.country.PowerStep;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.map.MapGenerator;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.WorldLimits;
import kolo.engine.wheel.AppliedModifier;
import org.junit.jupiter.api.Test;

/**
 * Бюджет сили (GD §4.11) на вбудованому контенті: ті самі світи заселяються з кожним коридором хоста. Зсув є рівно
 * тоді, коли сила поза коридором, і діє на наступне колесо; вужчий коридор тримає більше гравців у своїх межах.
 */
class BundledPowerBudgetIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int MAPS = 10;
    private static final int RUNS = 5;

    @Test
    void shiftKeepsPlayersCloserToMedian() {
        TreeMap<PowerCorridor, List<StartCountry>> players = new TreeMap<>();
        for (PowerCorridor corridor : PowerCorridor.values()) {
            List<StartCountry> generated = generate(corridor);
            generated.forEach(country -> check(country));
            players.put(
                    corridor,
                    generated.stream().filter(country -> !country.power().npc()).toList());
        }

        MedianRange narrow =
                PACK.balance().corridor(PowerCorridor.EQUAL_CHANCES).players();
        long shifted = players.get(PowerCorridor.EQUAL_CHANCES).stream()
                .filter(country -> country.power().steps().stream().anyMatch(step -> step.advantage() != 0))
                .count();
        assertThat(shifted).isPositive();
        assertThat(inside(players.get(PowerCorridor.EQUAL_CHANCES), narrow))
                .isGreaterThanOrEqualTo(inside(players.get(PowerCorridor.FULL_CHAOS), narrow));
    }

    @Test
    void sameWorldSameBudget() {
        assertThat(generate(PowerCorridor.EQUAL_CHANCES, 3))
                .extracting(StartCountry::power)
                .containsExactlyElementsOf(generate(PowerCorridor.EQUAL_CHANCES, 3).stream()
                        .map(StartCountry::power)
                        .toList());
    }

    private static void check(StartCountry country) {
        PowerBudget power = country.power();
        MedianRange range = power.range(PACK.balance());
        assertThat(power.steps()).extracting(PowerStep::component).containsExactly(PowerComponent.values());
        for (PowerStep step : power.steps()) {
            assertThat(Math.abs(step.advantage()))
                    .isLessThanOrEqualTo(PACK.balance().power().maxAdvantage());
            boolean outside = step.strengthPct() < range.minPct() || step.strengthPct() > range.maxPct();
            assertThat(step.advantage()).isEqualTo(PACK.balance().power().advantage(step.strengthPct(), range));
            if (!outside) {
                assertThat(step.advantage()).isZero();
            }
        }
        // Зсув після площі — у записі колеса населення.
        int afterArea = power.steps().getFirst().advantage();
        List<AppliedModifier> population =
                country.population().rolls().getFirst().modifiers();
        if (afterArea == 0) {
            assertThat(population)
                    .extracting(AppliedModifier::descriptionKey)
                    .doesNotContain(PowerBudget.DESCRIPTION_KEY);
        } else {
            assertThat(population)
                    .contains(new AppliedModifier(
                            PowerBudget.DESCRIPTION_KEY + ":" + PopulationWheel.KIND.id(),
                            PowerBudget.DESCRIPTION_KEY,
                            afterArea));
        }
    }

    /** Частка держав, чия підсумкова сила в межах {@code range}, у ‰. */
    private static long inside(List<StartCountry> countries, MedianRange range) {
        long count = countries.stream()
                .filter(country -> country.power().strengthPct() >= range.minPct()
                        && country.power().strengthPct() <= range.maxPct())
                .count();
        return count * 1_000 / countries.size();
    }

    private static List<StartCountry> generate(PowerCorridor corridor) {
        List<StartCountry> countries = new ArrayList<>();
        for (long seed = 0; seed < MAPS; seed++) {
            countries.addAll(generate(corridor, seed));
        }
        return countries;
    }

    private static List<StartCountry> generate(PowerCorridor corridor, long seed) {
        Rng worldRng = Rng.of(seed);
        WorldMap map = MapGenerator.generate(
                worldRng.fork("map"), PACK, WorldSizeInput.of(WorldLimits.MAX_PLAYERS, NpcShare.MANY));
        List<StartReligion> religions = WorldReligionsWheel.generate(worldRng.fork("religions"), PACK, map.countries())
                .religions();
        List<StartCountry> countries = new ArrayList<>();
        for (int run = 0; run < RUNS; run++) {
            for (int n = 0; n < map.countries(); n++) {
                CountryGenerationInput input =
                        new CountryGenerationInput(map, n, religions, corridor, new TreeSet<>(), new TreeSet<>());
                countries.add(CountryGenerator.generate(worldRng.fork("run:" + run + ":country:" + n), PACK, input));
            }
        }
        return countries;
    }
}
