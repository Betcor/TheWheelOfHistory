package kolo.engine.wheel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.Season;
import kolo.engine.util.Fixed;

/**
 * Застосування переваги, нормалізація й обертання колеса. Уся арифметика — цілочисельна.
 *
 * <p>Та сама логіка працює на клієнті для попереднього перегляду шансів: {@link #applyAdvantage} не потребує RNG.
 */
public final class Wheel {

    /** Сума ваг нормалізованого колеса: 100% у базисних пунктах. */
    public static final int TOTAL_BP = Fixed.BP_SCALE;

    /** Мінімальна вага критичних секторів (КП і КУ): 1%. */
    public static final int MIN_CRITICAL_BP = 100;

    /** Найбільша сила переваги {@code k}, %: при {@code k = 100} і {@code A = 100} успіх подвоюється, провал зникає. */
    public static final int MAX_STRENGTH = 100;

    private Wheel() {}

    /**
     * Застосовує перевагу й нормалізує колесо до {@link #TOTAL_BP}.
     *
     * <ol>
     *   <li>Сектори {@code SUCCESS}/{@code CRIT_SUCCESS} множаться на {@code (100 + A·k/100)/100}, {@code
     *       FAIL}/{@code CRIT_FAIL} — на {@code (100 − A·k/100)/100}, {@code PARTIAL} не змінюється. Добутки
     *       зберігаються точно (у 1/10 000 bp), тож округлення одне — під час нормалізації.
     *   <li>Нормалізація до 10 000 методом найбільших залишків: кожен сектор отримує {@code floor} своєї частки,
     *       решта пунктів — секторам з найбільшими залишками; при рівності — за {@code id} за зростанням.
     *   <li>Критичні сектори, що отримали менше {@link #MIN_CRITICAL_BP}, фіксуються на мінімумі, а решта бюджету
     *       нормалізується між іншими секторами заново — доки жоден критичний сектор не опиниться під мінімумом.
     * </ol>
     *
     * <p>Якщо перевага обнулила всі сектори, що підлягають нормалізації (напр. колесо лише з провалів при {@code A =
     * 100, k = 100}), вони діляться за базовими вагами; якщо й ті нульові — порівну. Колесо завжди лишається
     * валідним.
     *
     * @param sectors сектори в порядку контенту; порядок зберігається
     * @param advantage перевага {@code A}, {@code −100..100}
     * @param strength сила переваги {@code k} для цього типу колеса, {@code 0..100} (%, з балансу)
     * @return нові сектори з вагами, сума яких — рівно {@link #TOTAL_BP}
     */
    public static <T> List<Sector<T>> applyAdvantage(List<Sector<T>> sectors, int advantage, int strength) {
        validate(sectors);
        Checks.inRange("advantage", advantage, Advantage.MIN, Advantage.MAX);
        Checks.inRange("strength", strength, 0, MAX_STRENGTH);

        int shift = advantage * strength;
        long[] scaled = new long[sectors.size()];
        for (int i = 0; i < scaled.length; i++) {
            Sector<T> sector = sectors.get(i);
            int factor = TOTAL_BP;
            if (sector.tier().isSuccess()) {
                factor += shift;
            } else if (sector.tier().isFailure()) {
                factor -= shift;
            }
            scaled[i] = (long) sector.weightBp() * factor;
        }

        int[] weights = normalize(sectors, scaled);
        List<Sector<T>> result = new ArrayList<>(sectors.size());
        for (int i = 0; i < weights.length; i++) {
            result.add(sectors.get(i).withWeight(weights[i]));
        }
        return List.copyOf(result);
    }

    /**
     * Застосовує перевагу й крутить колесо: {@code r = rng.nextInt(10 000)}, результат — перший сектор у порядку
     * контенту, для якого {@code r} менше кумулятивної суми ваг.
     *
     * @param rng потік підсистеми, напр. {@code ctx.rng().fork("construction:" + countryId)}
     * @param strength сила переваги {@code k}, див. {@link #applyAdvantage}
     * @param season сезонна фаза війни або {@code null} поза війною
     */
    public static <T> WheelSpin<T> spin(
            Rng rng,
            WheelKind kind,
            List<Sector<T>> sectors,
            Advantage advantage,
            int strength,
            int turn,
            Season season) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(advantage, "advantage");
        List<Sector<T>> finalSectors = applyAdvantage(sectors, advantage.value(), strength);

        int roll = rng.nextInt(TOTAL_BP);
        Sector<T> outcome = pick(finalSectors, roll);

        List<RolledSector> rolled = new ArrayList<>(finalSectors.size());
        for (Sector<T> sector : finalSectors) {
            rolled.add(new RolledSector(sector.id(), sector.weightBp(), sector.tier(), sector.quality()));
        }
        RollRecord record = new RollRecord(
                kind, rolled, advantage.value(), advantage.modifiers(), outcome.id(), roll, turn, season);
        return new WheelSpin<>(outcome, record);
    }

    private static <T> Sector<T> pick(List<Sector<T>> sectors, int roll) {
        int cumulative = 0;
        for (Sector<T> sector : sectors) {
            cumulative += sector.weightBp();
            if (roll < cumulative) {
                return sector;
            }
        }
        // Недосяжно: applyAdvantage завжди повертає рівно TOTAL_BP.
        throw new InvariantViolationException(
                ErrorDetails.of("check", "wheel_total_bp", "expected", TOTAL_BP, "actual", cumulative));
    }

    private static <T> void validate(List<Sector<T>> sectors) {
        if (sectors.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "sectors"));
        }
        TreeSet<String> ids = new TreeSet<>();
        long total = 0;
        int critical = 0;
        for (Sector<T> sector : sectors) {
            if (!ids.add(sector.id())) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", "sector.id", "value", sector.id()));
            }
            total += sector.weightBp();
            if (sector.tier().isCritical()) {
                critical++;
            }
        }
        if (total == 0) {
            throw new ValidationException(ErrorCode.WHEEL_ZERO_WEIGHT, ErrorDetails.of());
        }
        if ((long) critical * MIN_CRITICAL_BP > TOTAL_BP) {
            throw new ValidationException(
                    ErrorCode.WHEEL_TOO_MANY_CRITICAL,
                    ErrorDetails.of("count", critical, "max", TOTAL_BP / MIN_CRITICAL_BP));
        }
    }

    /** Нормалізація з фіксацією критичних секторів на мінімумі (див. {@link #applyAdvantage}). */
    private static <T> int[] normalize(List<Sector<T>> sectors, long[] scaled) {
        int n = scaled.length;
        boolean[] pinned = new boolean[n];
        int pinnedCount = 0;
        while (true) {
            int[] weights = distribute(sectors, scaled, pinned, TOTAL_BP - pinnedCount * MIN_CRITICAL_BP);
            boolean pinnedMore = false;
            for (int i = 0; i < n; i++) {
                if (pinned[i]) {
                    weights[i] = MIN_CRITICAL_BP;
                } else if (sectors.get(i).tier().isCritical() && weights[i] < MIN_CRITICAL_BP) {
                    pinned[i] = true;
                    pinnedCount++;
                    pinnedMore = true;
                }
            }
            // Кожна ітерація фіксує хоча б один сектор, тож цикл скінченний. Незафіксовані не закінчуються:
            // критичних секторів не більше за 100, а некритичні ніколи не фіксуються.
            if (!pinnedMore) {
                return weights;
            }
        }
    }

    /** Ділить {@code budget} між незафіксованими секторами методом найбільших залишків. */
    private static <T> int[] distribute(List<Sector<T>> sectors, long[] scaled, boolean[] pinned, int budget) {
        int n = scaled.length;
        long[] source = scaled;
        long total = unpinnedSum(source, pinned);
        if (total == 0) {
            // Усі сектори обнулилися (у кожного той самий множник 0): пропорції до переваги — єдина розумна
            // підстава, бо при A, близькій до межі, ті самі сектори ділили б бюджет саме так.
            source = new long[n];
            for (int i = 0; i < n; i++) {
                source[i] = sectors.get(i).weightBp();
            }
            total = unpinnedSum(source, pinned);
        }
        if (total == 0) {
            source = new long[n];
            Arrays.fill(source, 1);
            total = unpinnedSum(source, pinned);
        }

        int[] weights = new int[n];
        long[] remainders = new long[n];
        List<Integer> free = new ArrayList<>();
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            if (pinned[i]) {
                continue;
            }
            long share = Math.multiplyExact(source[i], budget);
            weights[i] = (int) Math.floorDiv(share, total);
            remainders[i] = Math.floorMod(share, total);
            assigned += weights[i];
            free.add(i);
        }

        free.sort((a, b) -> {
            int byRemainder = Long.compare(remainders[b], remainders[a]);
            return byRemainder != 0
                    ? byRemainder
                    : sectors.get(a).id().compareTo(sectors.get(b).id());
        });
        for (int j = 0; j < budget - assigned; j++) {
            weights[free.get(j)]++;
        }
        return weights;
    }

    private static long unpinnedSum(long[] values, boolean[] pinned) {
        long sum = 0;
        for (int i = 0; i < values.length; i++) {
            if (!pinned[i]) {
                sum += values[i];
            }
        }
        return sum;
    }
}
