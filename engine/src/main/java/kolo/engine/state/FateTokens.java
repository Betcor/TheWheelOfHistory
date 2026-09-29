package kolo.engine.state;

/**
 * Жетони долі (GD §2.4): рідкісна валюта для перекручування коліс своєї держави.
 *
 * <p>Ліміт — правило гри, а не баланс: жетон понад {@link #MAX} згорає наприкінці року.
 */
public final class FateTokens {

    /** Найбільше жетонів, які держава може мати одночасно. */
    public static final int MAX = 3;

    private FateTokens() {}
}
