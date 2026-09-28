package kolo.engine.generation.country;

import java.util.Arrays;
import java.util.List;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;

/** Контент для тестів колеса ладу: ідеології з різними вагами й мітками, що перетинаються. */
final class TestRegime {

    static final SubIdeologyDef LIBERAL = sub("liberal_democracy", 100, "liberal");
    /** Мітка {@code monarch} є й в ідеології «монархія»: у ладі вона не повторюється. */
    static final SubIdeologyDef CONSTITUTIONAL = sub("constitutional_monarchy", 300, "monarch");

    static final IdeologyDef DEMOCRACY = ideology("democracy", 100, List.of("democratic"), LIBERAL, CONSTITUTIONAL);
    static final IdeologyDef MONARCHY =
            ideology("monarchy", 300, List.of("monarchic", "monarch"), sub("absolute_monarchy", 100, "absolutism"));

    static final ContentPack PACK =
            TestBackstory.pack(List.of(DEMOCRACY, MONARCHY), TestBackstory.FRAGMENTS, TestBackstory.COUNT);

    private TestRegime() {}

    static SubIdeologyDef sub(String id, int weight, String... tags) {
        return new SubIdeologyDef(new SubIdeologyId(id), "Підкласифікація " + id, weight, List.of(), List.of(tags));
    }

    static IdeologyDef ideology(String id, int weight, List<String> tags, SubIdeologyDef... subs) {
        return new IdeologyDef(new IdeologyId(id), "Ідеологія " + id, weight, List.of(), tags, Arrays.asList(subs));
    }
}
