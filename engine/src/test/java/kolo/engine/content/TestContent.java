package kolo.engine.content;

import java.util.Arrays;
import java.util.List;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.state.Development;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.Stat;
import kolo.engine.state.TechBranch;

/** Мінімальні валідні визначення для тестів моделі контенту. */
final class TestContent {

    static final String HASH = "0".repeat(64);

    private TestContent() {}

    static SubIdeologyDef sub(String id) {
        return new SubIdeologyDef(new SubIdeologyId(id), "Підкласифікація " + id, List.of(), List.of());
    }

    static IdeologyDef ideology(String id, String... subIds) {
        List<SubIdeologyDef> subs = Arrays.stream(subIds).map(TestContent::sub).toList();
        return new IdeologyDef(
                new IdeologyId(id),
                "Ідеологія " + id,
                List.of(new ModifierDef(ModifierTarget.stat(Stat.HDI), 5)),
                List.of(id),
                subs);
    }

    static DoctrineDef doctrine(String id) {
        return new DoctrineDef(new DoctrineId(id), "Доктрина " + id, List.of(), List.of("land"));
    }

    static ResourceDef resource(String id) {
        return new ResourceDef(new ResourceId(id), "Ресурс " + id, List.of("metal"));
    }

    /** По визначенню на кожну галузь. */
    static List<TechBranchDef> branches() {
        return Arrays.stream(TechBranch.values())
                .map(branch -> new TechBranchDef(branch, "Галузь " + branch.key()))
                .toList();
    }

    static DevelopmentLevelDef level(int level) {
        return new DevelopmentLevelDef(level, "Рівень " + level, "Опис рівня " + level);
    }

    /** По визначенню на кожен рівень розвиненості. */
    static List<DevelopmentLevelDef> levels() {
        return Development.levels().stream().map(TestContent::level).toList();
    }

    /** По визначенню на кожен ядерний статус. */
    static List<NuclearStatusDef> nuclearStatuses() {
        return Arrays.stream(NuclearStatus.values())
                .map(status -> new NuclearStatusDef(status, "Статус " + status.key(), List.of()))
                .toList();
    }

    static ContentPack pack(List<IdeologyDef> ideologies) {
        return new ContentPack(
                HASH,
                ideologies,
                List.of(doctrine("armored")),
                List.of(resource("iron")),
                branches(),
                levels(),
                nuclearStatuses());
    }
}
