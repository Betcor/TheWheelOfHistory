package kolo.engine.generation.country;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.content.StreakWheelDef;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import kolo.engine.wheel.WheelSpin;

/**
 * Колеса стріків генерації (GD §4.10): «Золота доба» ({@code generation_golden_age}) після кількох дуже добрих
 * результатів поспіль і «Андердог» ({@code generation_underdog}) після кількох дуже поганих. Коли крутити, вирішує
 * {@link Streaks}.
 *
 * <p>Одне обертання обирає одну нагороду з контенту; мітки колеса держава отримує за будь-якої нагороди. Перевага
 * не діє: сектори мають рівень {@link OutcomeTier#PARTIAL} і нейтральну якість {@value #QUALITY} — нагорода стріку
 * сама не рахується в стріки.
 */
public final class StreakWheel {

    /** Нагорода стріку не рахується в наступні стріки. */
    static final int QUALITY = 50;

    /** Генерація відбувається до першого ходу. */
    private static final int TURN = 0;

    private StreakWheel() {}

    /** Тип колеса стріку цього виду: {@code generation_<вид>}. */
    public static WheelKind kind(StreakKind streak) {
        return new WheelKind("generation_" + streak.key());
    }

    /** @param rng окремий потік стріку держави */
    public static StreakBonus generate(Rng rng, ContentPack content, StreakKind streak) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(streak, "streak");
        StreakWheelDef wheel = content.streaks().wheel(streak);
        WheelKind kind = kind(streak);
        WheelSpin<StreakRewardDef> spin = Wheel.spin(
                rng.fork("reward"),
                kind,
                sectors(wheel),
                Advantage.NONE,
                content.balance().wheel().strength(kind),
                TURN,
                null);
        StreakRewardDef reward = spin.value();
        TreeSet<String> tags = new TreeSet<>(wheel.tags());
        tags.addAll(reward.tags());
        return new StreakBonus(streak, reward, tags, spin.record());
    }

    /** Сектор на кожну нагороду в порядку контенту; id сектора — id нагороди. */
    static List<Sector<StreakRewardDef>> sectors(StreakWheelDef wheel) {
        List<Sector<StreakRewardDef>> sectors = new ArrayList<>();
        for (StreakRewardDef reward : wheel.rewards()) {
            sectors.add(new Sector<>(
                    reward.id().value(), reward.weight(), reward, QUALITY, OutcomeTier.PARTIAL, reward.tags()));
        }
        return sectors;
    }
}
