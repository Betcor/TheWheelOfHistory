package kolo.engine.content;

import java.util.List;
import java.util.TreeMap;
import kolo.engine.state.NpcShare;

/**
 * Мінімальний валідний контент карти для тестових пакетів: шаблони «Пангея» (1 материк, коефіцієнт 100) і
 * «Архіпелаг» (3–5 материків, коефіцієнт 150); баланс розміру світу — {@link #BALANCE}.
 */
public final class TestMaps {

    public static final MapTemplateDef PANGAEA = template("pangaea", 100, 100, 1, 1);
    public static final MapTemplateDef ARCHIPELAGO = template("archipelago", 100, 150, 3, 5);

    public static final MapContent CONTENT = new MapContent(List.of(PANGAEA, ARCHIPELAGO));

    /** NPC: мало 0–1, звичайно 2–4, багато 10–20; 60–100 провінцій на державу з кроком 20; 5–15% нічийних; 100–3000. */
    public static final WorldBalanceDef BALANCE = world(new CountRange(100, 3000));

    private TestMaps() {}

    public static MapTemplateDef template(
            String id, int weight, int provincesPct, int minContinents, int maxContinents) {
        return new MapTemplateDef(
                new MapTemplateId(id),
                "Шаблон " + id,
                "Опис шаблону " + id,
                weight,
                provincesPct,
                new CountRange(minContinents, maxContinents));
    }

    /** Баланс {@link #BALANCE} з іншими межами кількості провінцій. */
    public static WorldBalanceDef world(CountRange provinces) {
        TreeMap<NpcShare, CountRange> npc = new TreeMap<>();
        npc.put(NpcShare.FEW, new CountRange(0, 1));
        npc.put(NpcShare.NORMAL, new CountRange(2, 4));
        npc.put(NpcShare.MANY, new CountRange(10, 20));
        return new WorldBalanceDef(npc, new StepRange(60, 100, 20), new StepRange(500, 1500, 500), provinces);
    }
}
