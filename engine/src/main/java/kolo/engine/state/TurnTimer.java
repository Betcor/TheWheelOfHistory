package kolo.engine.state;

import java.util.Locale;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Таймер ходу (GD §6.1) — параметр сесії, який задає хост: скільки триває фаза наказів. Коли час вийшов, рік
 * розв'язується, і за гравців, що не натиснули «Готово», діє автопілот (GD §6.3). У ручному режимі таймера немає: рік
 * розв'язується, коли «Готово» натиснули всі (або хост вручну).
 *
 * @param mode режим
 * @param seconds тривалість фази наказів; у ручному режимі — 0, інакше {@code 1..}{@value #MAX_SECONDS}
 */
public record TurnTimer(Mode mode, int seconds) {

    /** Найдовша фаза наказів — тиждень: запобіжник від помилки в контенті, а не правило гри. */
    public static final int MAX_SECONDS = 7 * 24 * 60 * 60;

    /** Без таймера. */
    public static final TurnTimer MANUAL = new TurnTimer(Mode.MANUAL, 0);

    public TurnTimer {
        Objects.requireNonNull(mode, "mode");
        if (mode == Mode.MANUAL) {
            if (seconds != 0) {
                throw new ValidationException(
                        ErrorCode.CONFLICTING_FIELDS, ErrorDetails.of("field", "seconds", "mode", mode.key()));
            }
        } else {
            Checks.inRange("seconds", seconds, 1, MAX_SECONDS);
        }
    }

    /** Чи фаза наказів обмежена в часі. */
    public boolean timed() {
        return mode != Mode.MANUAL;
    }

    /** Режим таймера. */
    public enum Mode {
        /** «Ручний»: рік розв'язується, коли всі натиснули «Готово», або хост вручну. */
        MANUAL,
        /** «Живий»: хвилини на хід. */
        LIVE,
        /** «Асинхронний»: години на хід, для гри через сервер. */
        ASYNC;

        /** Ключ у протоколі й файлі світу, напр. {@code live}. */
        public String key() {
            // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
