package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.CountryNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.LocalizedName;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо назви (GD §4.1, колесо 16; GD §4.9).
 *
 * <p>Перед обертанням складається {@code generation.name_candidates} готових назв-кандидатів: кожна — рівноймовірні
 * мовний стиль, форма державності, доступна підкласифікації, і корінь ({@link CountryNames}). Колесо {@link #KIND}
 * має по рівному сектору {@code name_<n>} на кандидата й обирає назву держави; її стилем потім звуть людей держави.
 *
 * <p>Унікальна в світі повна назва, а не корінь: «Королівство Велмар» і «Народна Республіка Велмар» можуть
 * співіснувати. Кандидат із зайнятою повною назвою перегенеровується, кандидати не повторюють і один одного.
 *
 * <p>Перевага не діє: сектори мають рівень {@link OutcomeTier#PARTIAL} і нейтральну якість {@value #QUALITY} — назва
 * не робить старт кращим чи гіршим.
 */
public final class NameWheel {

    public static final WheelKind KIND = new WheelKind("generation_name");

    /** Префікс id сектора; номер кандидата — з одиниці. */
    static final String SECTOR_PREFIX = "name_";

    /** Назва нейтральна для держави. */
    static final int QUALITY = 50;

    /**
     * Скільки разів перегенеровувати кандидата із зайнятою повною назвою. Вбудований контент дає тисячі назв на
     * підкласифікацію, тож вичерпати спроби — ознака бракованого контенту чи бага.
     */
    static final int NAME_ATTEMPTS = 100;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private NameWheel() {}

    /**
     * @param rng окремий потік назви держави; всередині розгалужується на кожного кандидата ({@code candidate:<n>}) і на
     *     обертання ({@code wheel}), тож зайняті назви змінюють кандидатів, але не номер сектора, що випав
     * @param takenNames повні назви держав у називному відмінку, вже зайняті в світі
     * @throws ValidationException якщо підкласифікації немає в контенті
     * @throws InvariantViolationException якщо за {@value #NAME_ATTEMPTS} спроб не вдалося скласти вільну назву
     */
    public static StartName generate(Rng rng, ContentPack content, SubIdeologyId subIdeology, Set<String> takenNames) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        // Невідома підкласифікація — помилка входу, ще до будь-якого кидка.
        content.stateFormsFor(subIdeology);
        TreeSet<String> taken = new TreeSet<>(takenNames);

        List<NameCandidate> candidates = new ArrayList<>();
        int count = content.balance().generation().nameCandidates();
        for (int i = 1; i <= count; i++) {
            candidates.add(candidate(rng.fork("candidate:" + i), content, subIdeology, taken));
        }
        WheelSpin<Integer> spin = Wheel.spin(
                rng.fork("wheel"),
                KIND,
                sectors(candidates.size()),
                Advantage.NONE,
                content.balance().wheel().strength(KIND),
                TURN,
                null);
        return new StartName(candidates, spin.value(), spin.record());
    }

    private static NameCandidate candidate(
            Rng rng, ContentPack content, SubIdeologyId subIdeology, TreeSet<String> taken) {
        List<NameStyleId> styles = List.copyOf(content.names().styles().keySet());
        for (int attempt = 0; attempt < NAME_ATTEMPTS; attempt++) {
            NameStyleId style = styles.get(rng.nextInt(styles.size()));
            LocalizedName name = CountryNames.generate(rng, content, subIdeology, style);
            if (taken.add(name.fullName().nominative())) {
                return new NameCandidate(style, name);
            }
        }
        throw new InvariantViolationException(ErrorDetails.of(
                "check", "country_name_unique", "value", subIdeology.value(), "attempts", NAME_ATTEMPTS));
    }

    /** Рівні сектори {@code name_1..name_<count>}; значення — індекс кандидата з нуля. */
    static List<Sector<Integer>> sectors(int count) {
        List<Sector<Integer>> sectors = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            sectors.add(new Sector<>(sectorId(index), 1, index, QUALITY, OutcomeTier.PARTIAL, List.of()));
        }
        return sectors;
    }

    /** Id сектора кандидата з цим індексом (з нуля). */
    static String sectorId(int index) {
        return SECTOR_PREFIX + (index + 1);
    }
}
