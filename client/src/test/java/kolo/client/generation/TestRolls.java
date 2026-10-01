package kolo.client.generation;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.WheelKind;

/** Записи коліс для тестів показу генерації. */
final class TestRolls {

    private TestRolls() {}

    /** Колесо з рівними секторами {@code ids}, що випало на {@code result}. */
    static RollRecord roll(String kind, String result, String... ids) {
        List<RolledSector> sectors = new ArrayList<>();
        int left = 10_000;
        for (int i = 0; i < ids.length; i++) {
            int weight = i + 1 == ids.length ? left : 10_000 / ids.length;
            left -= weight;
            sectors.add(new RolledSector(ids[i], weight, OutcomeTier.PARTIAL, 50));
        }
        int roll = 0;
        for (RolledSector sector : sectors) {
            if (sector.id().equals(result)) {
                break;
            }
            roll += sector.weightBp();
        }
        return new RollRecord(new WheelKind(kind), sectors, 0, List.of(), result, roll, 0, null);
    }

    static RollRecord roll(String kind) {
        return roll(kind, "a", "a", "b");
    }
}
