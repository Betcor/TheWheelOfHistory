package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DogmaDef;
import kolo.engine.content.DogmaId;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.ReligionContent;
import kolo.engine.content.ReligionPolityDef;
import kolo.engine.content.SecularStateDef;
import kolo.engine.content.StateReligionDef;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.SourceKind;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо релігії держави (GD §4.1, 6а; §25.2): державна релігія або світська держава.
 *
 * <p>Сектори — релігії світу в порядку генерації, кожна з однаковою вагою {@code state_religion.religion_weight}, і
 * світська держава з вагою за мітками ладу; якщо умова світської держави не виконана (теократія) або вага 0, сектору
 * немає. Вибір не буває кращим чи гіршим: сектори мають рівень {@link OutcomeTier#PARTIAL}, нейтральну якість
 * {@value #QUALITY}, перевага не діє.
 *
 * <p>Держава отримує мітки релігії (або світської держави) і постійні модифікатори догматів і устрою своєї віри.
 */
public final class StateReligionWheel {

    public static final WheelKind KIND = new WheelKind("generation_state_religion");

    /** Id сектору світської держави; сектори релігій — {@code religion_<номер>}. */
    public static final String SECULAR = "secular";

    /** Релігія держави нейтральна для стріків генерації. */
    static final int QUALITY = 50;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private StateReligionWheel() {}

    /**
     * @param rng окремий потік релігії держави
     * @param tags мітки держави на момент колеса — ладу ({@link Regime#tags()})
     * @param religions релігії світу в порядку генерації
     * @throws InvariantViolationException якщо релігій світу немає, а світською держава бути не може
     */
    public static StartStateReligion generate(
            Rng rng, ContentPack content, Set<String> tags, List<StartReligion> religions) {
        Objects.requireNonNull(rng, "rng");
        ReligionContent religionContent = content.religions();
        List<Sector<OptionalInt>> sectors = sectors(religionContent.stateReligion(), tags, religions);
        if (sectors.isEmpty()) {
            throw new InvariantViolationException(
                    ErrorDetails.of("check", "state_religion_available", "value", religions.size()));
        }
        WheelSpin<OptionalInt> spin = Wheel.spin(
                rng.fork("religion"),
                KIND,
                sectors,
                Advantage.NONE,
                content.balance().wheel().strength(KIND),
                TURN,
                null);
        OptionalInt chosen = spin.value();
        if (chosen.isEmpty()) {
            return new StartStateReligion(
                    chosen,
                    new TreeSet<>(religionContent.stateReligion().secular().tags()),
                    List.of(),
                    List.of(spin.record()));
        }
        StartReligion religion = religions.get(chosen.getAsInt());
        return new StartStateReligion(
                chosen, religion.tags(), modifiers(religionContent, religion), List.of(spin.record()));
    }

    /**
     * Сектори релігій світу в порядку генерації (id {@code religion_<номер>}), потім світська держава, якщо її вага за
     * мітками {@code > 0}.
     */
    static List<Sector<OptionalInt>> sectors(StateReligionDef def, Set<String> tags, List<StartReligion> religions) {
        List<Sector<OptionalInt>> sectors = new ArrayList<>();
        for (int i = 0; i < religions.size(); i++) {
            sectors.add(sector("religion_" + i, def.religionWeight(), OptionalInt.of(i)));
        }
        SecularStateDef secular = def.secular();
        int weight = secular.weightFor(tags);
        if (weight > 0) {
            sectors.add(sector(SECULAR, weight, OptionalInt.empty()));
        }
        return sectors;
    }

    /**
     * Постійні модифікатори державної релігії: спершу догматів у порядку релігії, потім устрою, кожні в порядку
     * контенту. Id — {@code dogma:<id>:<номер>} і {@code religion_polity:<id>:<номер>}; ключ пояснення — {@code
     * dogma.<id>} і {@code religion_polity.<id>}.
     */
    static List<Modifier> modifiers(ReligionContent content, StartReligion religion) {
        List<Modifier> result = new ArrayList<>();
        for (DogmaId id : religion.dogmas()) {
            DogmaDef dogma = content.dogma(id).orElseThrow(() -> unknown("dogma", id.value()));
            add(result, "dogma", id.value(), dogma.modifiers());
        }
        ReligionPolityDef polity = content.polity(religion.polity())
                .orElseThrow(() -> unknown("religion_polity", religion.polity().value()));
        add(result, "religion_polity", polity.id().value(), polity.modifiers());
        return List.copyOf(result);
    }

    private static void add(List<Modifier> target, String prefix, String refId, List<ModifierDef> defs) {
        ModifierSource source = new ModifierSource(SourceKind.RELIGION, refId);
        for (int i = 0; i < defs.size(); i++) {
            target.add(defs.get(i).toModifier(prefix + ":" + refId + ":" + i, source, null, prefix + "." + refId));
        }
    }

    /** Релігію згенеровано з іншого контенту: для цього пакета це стан, якого не може бути. */
    private static InvariantViolationException unknown(String check, String value) {
        return new InvariantViolationException(ErrorDetails.of("check", "state_religion_" + check, "value", value));
    }

    private static Sector<OptionalInt> sector(String id, int weight, OptionalInt value) {
        return new Sector<>(id, weight, value, QUALITY, OutcomeTier.PARTIAL, List.of());
    }
}
