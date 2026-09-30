package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.CoastLevelDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.TerrainTagDef;
import kolo.engine.generation.map.ClimateMap;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.generation.map.SeaMap;
import kolo.engine.state.Terrain;

/** Незалежна перевірка географії держави: усе перераховується з карти напряму. */
final class GeographyChecks {

    private GeographyChecks() {}

    static void assertValid(
            ContentPack pack,
            List<Integer> provinces,
            SeaMap sea,
            ClimateMap climate,
            FertilityMap fertility,
            StartGeography geography) {
        assertThat(geography.provinces()).containsExactlyElementsOf(new TreeSet<>(provinces));
        List<Integer> coastal = new ArrayList<>();
        TreeSet<Integer> zones = new TreeSet<>();
        TreeMap<Terrain, Integer> terrains = new TreeMap<>();
        long fertilitySum = 0;
        for (int cell : new TreeSet<>(provinces)) {
            if (!sea.seaZones(cell).isEmpty()) {
                coastal.add(cell);
                zones.addAll(sea.seaZones(cell));
            }
            terrains.merge(climate.terrain(cell).orElseThrow(), 1, Integer::sum);
            fertilitySum += fertility.fertility(cell).orElseThrow();
        }
        int count = geography.provinces().size();
        assertThat(geography.coastal()).isEqualTo(coastal);
        assertThat(geography.seaZones()).containsExactlyElementsOf(zones);
        assertThat(geography.terrains()).isEqualTo(terrains);
        assertThat(geography.fertility()).isEqualTo((int) (fertilitySum / count));
        Terrain dominant = terrains.entrySet().stream()
                .max((a, b) -> a.getValue().equals(b.getValue())
                        ? b.getKey().compareTo(a.getKey())
                        : Integer.compare(a.getValue(), b.getValue()))
                .orElseThrow()
                .getKey();
        assertThat(geography.dominant()).isEqualTo(dominant);

        // Рівень берега — останній, чий поріг не більший за частку, округлену вгору.
        int coastalPct = (coastal.size() * 100 + count - 1) / count;
        CoastLevelDef coast = null;
        for (CoastLevelDef level : pack.map().geography().coast()) {
            if (level.minPct() <= coastalPct) {
                coast = level;
            }
        }
        assertThat(geography.coast()).isEqualTo(coast.id());
        TreeSet<String> tags = new TreeSet<>(coast.tags());
        for (TerrainTagDef rule : pack.map().geography().terrains()) {
            int matching = rule.terrains().stream()
                    .mapToInt(kind -> terrains.getOrDefault(kind, 0))
                    .sum();
            if ((matching * 100 + count - 1) / count >= rule.minPct()) {
                tags.addAll(rule.tags());
            }
        }
        assertThat(geography.tags()).containsExactlyElementsOf(tags);
        assertThat(geography.hasCoast()).isEqualTo(!coastal.isEmpty());
    }
}
