package kolo.engine.state;

/**
 * Межі кількості держав у світі (GD §3.2) — правила гри, не баланс: під них розраховано розмір карти й бюджети
 * продуктивності.
 */
public final class WorldLimits {

    /** Одиночна гра — один гравець. */
    public static final int MIN_PLAYERS = 1;

    /** Тимчасова верхня межа гравців (відкрите питання GD §24). */
    public static final int MAX_PLAYERS = 16;

    /** Держав разом — гравців і NPC. */
    public static final int MAX_COUNTRIES = 40;

    private WorldLimits() {}
}
