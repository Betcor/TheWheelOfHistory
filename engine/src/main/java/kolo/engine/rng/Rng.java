package kolo.engine.rng;

/**
 * Потік псевдовипадкових чисел рушія.
 *
 * <p>Кожен потік має власний seed і послідовність. Незалежні підсистеми отримують власні потоки через
 * {@link #fork(String)}: дочірній потік залежить лише від seed-а батьківського й мітки, а не від того, скільки
 * чисел батьківський уже видав. Тому новий кидок в одній підсистемі не зсуває послідовностей в інших.
 *
 * <p>Не потокобезпечний: рушій однопотоковий.
 */
public final class Rng {

    private final long seed;
    private final Xoshiro256StarStar generator;

    private Rng(long seed) {
        this.seed = seed;
        this.generator = new Xoshiro256StarStar(seed);
    }

    public static Rng of(long seed) {
        return new Rng(seed);
    }

    /**
     * Змішує два значення в новий seed, напр. seed року {@code mix(worldSeed, turn)}.
     *
     * <p>Несиметрична: {@code mix(a, b) != mix(b, a)} у загальному випадку. При фіксованому одному аргументі —
     * біекція за іншим, тож різні роки одного світу ніколи не отримують однаковий seed.
     */
    public static long mix(long a, long b) {
        return SplitMix64.mix(SplitMix64.mix(a + SplitMix64.GOLDEN_GAMMA) ^ b);
    }

    public long seed() {
        return seed;
    }

    /**
     * Дочірній потік для підсистеми, напр. {@code fork("economy:" + countryId)}.
     *
     * <p>Однакова мітка від того самого батьківського seed-а завжди дає той самий потік.
     */
    public Rng fork(String label) {
        return new Rng(mix(seed, labelHash(label)));
    }

    public long nextLong() {
        return generator.nextLong();
    }

    /**
     * Рівномірне ціле з {@code [0, bound)} без зсуву: значення з неповного останнього «кошика» відкидаються й
     * генеруються наново.
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound має бути додатним: " + bound);
        }
        // Старші 31 біт: у xoshiro256** вони найякісніші, а невід'ємне int спрощує перевірку переповнення.
        int bits = (int) (nextLong() >>> 33);
        int value = bits % bound;
        // bits - value — початок кошика; якщо кошик виходить за Integer.MAX_VALUE, він неповний.
        while (bits - value + (bound - 1) < 0) {
            bits = (int) (nextLong() >>> 33);
            value = bits % bound;
        }
        return value;
    }

    /**
     * FNV-1a (64 біт) за UTF-16 кодовими одиницями рядка.
     *
     * <p>Не {@link String#hashCode()}: його 32 біти дають забагато колізій, а явний алгоритм не залежить від JDK.
     */
    static long labelHash(String label) {
        long hash = 0xCBF29CE484222325L;
        for (int i = 0; i < label.length(); i++) {
            hash ^= label.charAt(i);
            hash *= 0x100000001B3L;
        }
        return hash;
    }
}
