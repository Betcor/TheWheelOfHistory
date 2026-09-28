package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колесо передісторії (GD §4.7): кілька фрагментів поспіль, кожен наступний — з урахуванням міток попередніх.
 *
 * <ol>
 *   <li>Колесо {@link #COUNT_KIND} з рівними секторами обирає кількість фрагментів у межах балансу
 *       ({@code generation.backstory_fragments}).
 *   <li>Колесо {@link #FRAGMENT_KIND} обирає кожен фрагмент серед доступних: умова виконується для поточних міток,
 *       фрагмент ще не обраний, його вага для поточних міток більша за нуль і він міг статися не раніше за попередні
 *       ({@code years.to ≥ years.from} кожного обраного). Вага сектора — {@link BackstoryFragmentDef#weightFor}, якість —
 *       якість фрагмента (для стріків), мітки — {@code adds}.
 *   <li>Коли фрагменти обрано, їм рівноймовірно призначаються роки в порядку вибору: кожен не раніший за
 *       попередній (причина не стоїть після наслідку) і такий, що лишає місце наступним.
 *   <li>Сусід обирається рівноймовірно серед кандидатів при першому фрагменті з {@code neighbor} і лишається тим
 *       самим для всієї передісторії. Без кандидатів такі фрагменти недоступні.
 * </ol>
 *
 * <p>Перевага на колеса передісторії не діє: сектори мають рівень {@link OutcomeTier#PARTIAL}, ваги — лише з
 * контенту. Якщо доступних фрагментів забракло, передісторія коротша за обрану кількість.
 */
public final class BackstoryWheel {

    public static final WheelKind COUNT_KIND = new WheelKind("generation_backstory_count");
    public static final WheelKind FRAGMENT_KIND = new WheelKind("generation_backstory");

    /** Кількість фрагментів нейтральна для держави: не впливає на стріки ні в який бік. */
    static final int COUNT_QUALITY = 50;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private BackstoryWheel() {}

    /**
     * @param rng окремий потік передісторії держави; всередині розгалужується на кількість, фрагменти, роки й сусіда,
     *     тож зміна одного кидка не зсуває інших
     * @param tags мітки держави з попередніх коліс генерації (ідеологія, підкласифікація, ядерний статус…)
     * @param neighbors держави, з якими передісторія може пов'язати цю; порядок не важливий
     */
    public static Backstory generate(Rng rng, ContentPack content, Set<String> tags, Collection<CountryId> neighbors) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        TreeSet<String> currentTags = new TreeSet<>(tags);
        // Кандидати сортуються: результат не залежить від порядку, у якому їх передали.
        List<CountryId> candidates = List.copyOf(new TreeSet<>(neighbors));

        Rng countRng = rng.fork("count");
        Rng fragmentRng = rng.fork("fragments");
        Rng yearRng = rng.fork("years");
        Rng neighborRng = rng.fork("neighbor");

        List<RollRecord> rolls = new ArrayList<>();
        WheelSpin<Integer> countSpin = spin(content, countRng, COUNT_KIND, countSectors(content));
        rolls.add(countSpin.record());

        List<BackstoryFragmentDef> fragments = new ArrayList<>();
        TreeSet<BackstoryFragmentId> chosen = new TreeSet<>();
        CountryId neighbor = null;
        int earliest = BackstoryFragmentDef.EARLIEST_YEAR;
        for (int i = 0; i < countSpin.value(); i++) {
            List<Sector<BackstoryFragmentDef>> sectors =
                    fragmentSectors(content, currentTags, !candidates.isEmpty(), chosen, earliest);
            if (sectors.isEmpty()) {
                break;
            }
            WheelSpin<BackstoryFragmentDef> spin = spin(content, fragmentRng, FRAGMENT_KIND, sectors);
            rolls.add(spin.record());
            BackstoryFragmentDef fragment = spin.value();

            fragments.add(fragment);
            chosen.add(fragment.id());
            currentTags.addAll(fragment.adds());
            earliest = Math.max(earliest, fragment.yearFrom());
            if (fragment.neighbor() && neighbor == null) {
                neighbor = candidates.get(neighborRng.nextInt(candidates.size()));
            }
        }
        return new Backstory(years(yearRng, fragments), Optional.ofNullable(neighbor), currentTags, rolls);
    }

    /**
     * Роки обраних фрагментів у хронологічному порядку.
     *
     * <p>Рік кожного фрагмента обмежений зверху найменшим {@code years.to} серед нього й наступних: інакше ранній
     * фрагмент міг би випасти на 1969 рік і не лишити місця наступним. Проміжок ніколи не порожній, бо фрагмент
     * обирається, лише якщо {@code years.to} не раніше за {@code years.from} усіх попередніх.
     */
    private static List<BackstoryEntry> years(Rng rng, List<BackstoryFragmentDef> fragments) {
        int[] latest = new int[fragments.size()];
        int bound = BackstoryFragmentDef.LATEST_YEAR;
        for (int i = fragments.size() - 1; i >= 0; i--) {
            bound = Math.min(bound, fragments.get(i).yearTo());
            latest[i] = bound;
        }
        List<BackstoryEntry> entries = new ArrayList<>(fragments.size());
        int previous = BackstoryFragmentDef.EARLIEST_YEAR;
        for (int i = 0; i < fragments.size(); i++) {
            BackstoryFragmentDef fragment = fragments.get(i);
            int from = Math.max(fragment.yearFrom(), previous);
            int year = from + rng.nextInt(latest[i] - from + 1);
            entries.add(new BackstoryEntry(fragment, year));
            previous = year;
        }
        return entries;
    }

    /** Рівні сектори «{@code fragments_<n>}» для кожної кількості з балансу. */
    static List<Sector<Integer>> countSectors(ContentPack content) {
        CountRange range = content.balance().generation().backstoryFragments();
        List<Sector<Integer>> sectors = new ArrayList<>();
        for (int count = range.min(); count <= range.max(); count++) {
            sectors.add(new Sector<>("fragments_" + count, 1, count, COUNT_QUALITY, OutcomeTier.PARTIAL, List.of()));
        }
        return sectors;
    }

    static List<Sector<BackstoryFragmentDef>> fragmentSectors(
            ContentPack content, Set<String> tags, boolean hasNeighbor, Set<BackstoryFragmentId> chosen, int earliest) {
        List<Sector<BackstoryFragmentDef>> sectors = new ArrayList<>();
        for (BackstoryFragmentDef fragment : content.backstory().available(tags, hasNeighbor)) {
            int weight = fragment.weightFor(tags);
            if (weight == 0 || chosen.contains(fragment.id()) || fragment.yearTo() < earliest) {
                continue;
            }
            sectors.add(new Sector<>(
                    fragment.id().value(), weight, fragment, fragment.quality(), OutcomeTier.PARTIAL, fragment.adds()));
        }
        return sectors;
    }

    private static <T> WheelSpin<T> spin(ContentPack content, Rng rng, WheelKind kind, List<Sector<T>> sectors) {
        int strength = content.balance().wheel().strength(kind);
        return Wheel.spin(rng, kind, sectors, Advantage.NONE, strength, TURN, null);
    }
}
