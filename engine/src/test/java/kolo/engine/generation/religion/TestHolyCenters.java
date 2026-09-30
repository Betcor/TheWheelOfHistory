package kolo.engine.generation.religion;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import kolo.engine.content.ContentPack;
import kolo.engine.content.HolyCenterDef;
import kolo.engine.content.ReligionBalanceDef;
import kolo.engine.content.TestReligions;
import kolo.engine.generation.map.MapGenerator;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;

/** Фікстура колеса святого центру: карта тестового пакета й розподіл релігій між державами. */
final class TestHolyCenters {

    static final ContentPack PACK = TestNames.PACK;

    /** Карта з кількома державами й нічийною землею. */
    static final WorldMap MAP = MapGenerator.generate(Rng.of(1961), PACK, WorldSizeInput.of(2, NpcShare.NORMAL));

    private TestHolyCenters() {}

    /** Пакет з іншими числами святого центру; решта — як у {@link #PACK}. */
    static ContentPack pack(HolyCenterDef holyCenter) {
        ReligionBalanceDef base = TestReligions.BALANCE;
        return TestNames.pack(
                TestReligions.content(),
                new ReligionBalanceDef(base.count(), base.aspects(), base.dogmas(), holyCenter));
    }

    /** Держава {@code n} сповідує релігію {@code n % religions}, якщо {@code religions > 0}; інакше всі світські. */
    static List<OptionalInt> roundRobin(int religions) {
        List<OptionalInt> result = new ArrayList<>();
        for (int n = 0; n < MAP.countries(); n++) {
            result.add(religions == 0 ? OptionalInt.empty() : OptionalInt.of(n % religions));
        }
        return result;
    }

    /** Усі держави світські. */
    static List<OptionalInt> secular() {
        return roundRobin(0);
    }
}
