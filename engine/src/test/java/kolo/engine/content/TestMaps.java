package kolo.engine.content;

import java.util.List;
import java.util.TreeMap;
import kolo.engine.state.NpcShare;

/**
 * Мінімальний валідний контент карти для тестових пакетів: шаблони «Пангея» (1 материк, коефіцієнт 100, суходолу 50%)
 * і «Архіпелаг» (3–5 материків, коефіцієнт 150, суходолу 30%); баланс розміру світу — {@link #BALANCE}.
 */
public final class TestMaps {

    public static final MapTemplateDef PANGAEA = template("pangaea", 100, 100, 50, 1, 1);
    public static final MapTemplateDef ARCHIPELAGO = template("archipelago", 100, 150, 30, 3, 5);

    /** Комірка 20 одиниць, карта 2:1, одна ітерація Ллойда — дрібні карти для швидких тестів. */
    public static final MapGridDef GRID = new MapGridDef(20, 2, 1, 1);

    /** Вага материка 1–3, щонайменше 10 провінцій, береги наполовину з шуму, плями по 4 комірки. */
    public static final ContinentsDef CONTINENTS = new ContinentsDef(new CountRange(1, 3), 10, 50, 4);

    public static final MapContent CONTENT = new MapContent(List.of(PANGAEA, ARCHIPELAGO), GRID, CONTINENTS);

    /** NPC: мало 0–1, звичайно 2–4, багато 10–20; 60–100 провінцій на державу з кроком 20; 5–15% нічийних; 100–3000. */
    public static final WorldBalanceDef BALANCE = world(new CountRange(100, 3000));

    private TestMaps() {}

    /** Шаблон із суходолом 50%. */
    public static MapTemplateDef template(
            String id, int weight, int provincesPct, int minContinents, int maxContinents) {
        return template(id, weight, provincesPct, 50, minContinents, maxContinents);
    }

    public static MapTemplateDef template(
            String id, int weight, int provincesPct, int landPct, int minContinents, int maxContinents) {
        return new MapTemplateDef(
                new MapTemplateId(id),
                "Шаблон " + id,
                "Опис шаблону " + id,
                weight,
                provincesPct,
                landPct,
                new CountRange(minContinents, maxContinents));
    }

    /** Контент {@link #CONTENT} з іншими шаблонами й числами материків. */
    public static MapContent content(List<MapTemplateDef> templates, ContinentsDef continents) {
        return new MapContent(templates, GRID, continents);
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
