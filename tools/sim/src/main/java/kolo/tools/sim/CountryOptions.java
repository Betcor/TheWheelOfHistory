package kolo.tools.sim;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;

/**
 * Параметри команди {@code country}.
 *
 * @param seed seed світу: з нього — карта, релігії світу й усі держави
 * @param players скільки гравців у світі, {@value WorldLimits#MIN_PLAYERS}..{@value WorldLimits#MAX_PLAYERS}
 * @param npcShare частка NPC-держав, яку задав би хост
 * @param country номер держави, чию картку друкувати; чи є така в світі, видно лише після генерації карти
 * @param rolls чи друкувати всі обертання коліс
 * @param content каталог з YAML-файлами контенту; порожньо — вбудований контент
 */
record CountryOptions(long seed, int players, NpcShare npcShare, int country, boolean rolls, Optional<Path> content) {

    static final int DEFAULT_PLAYERS = 1;
    static final NpcShare DEFAULT_NPC_SHARE = NpcShare.NORMAL;

    CountryOptions {
        Objects.requireNonNull(npcShare, "npcShare");
        Objects.requireNonNull(content, "content");
    }

    /**
     * @param args параметри після назви команди
     * @throws UsageException якщо параметр невідомий, повторюється, бракує значення, значення не число чи поза
     *     межами, або не задано {@code --seed}
     */
    static CountryOptions parse(List<String> args) {
        Long seed = null;
        Integer players = null;
        NpcShare npcShare = null;
        Integer country = null;
        boolean rolls = false;
        Path content = null;
        for (int i = 0; i < args.size(); i++) {
            String option = args.get(i);
            switch (option) {
                case "--seed" -> {
                    once(option, seed);
                    seed = parseLong(option, value(args, ++i, option));
                }
                case "--players" -> {
                    once(option, players);
                    players = parseInt(
                            option, value(args, ++i, option), WorldLimits.MIN_PLAYERS, WorldLimits.MAX_PLAYERS);
                }
                case "--npc" -> {
                    once(option, npcShare);
                    npcShare = npcShare(value(args, ++i, option));
                }
                case "--country" -> {
                    once(option, country);
                    country = parseInt(option, value(args, ++i, option), 0, WorldLimits.MAX_COUNTRIES - 1);
                }
                case "--rolls" -> {
                    if (rolls) {
                        throw new UsageException("error.usage.duplicate_option", option);
                    }
                    rolls = true;
                }
                case "--content" -> {
                    once(option, content);
                    content = Path.of(value(args, ++i, option));
                }
                default -> throw new UsageException("error.usage.unknown_option", option);
            }
        }
        if (seed == null) {
            throw new UsageException("error.usage.missing_seed");
        }
        return new CountryOptions(
                seed,
                players == null ? DEFAULT_PLAYERS : players,
                npcShare == null ? DEFAULT_NPC_SHARE : npcShare,
                country == null ? 0 : country,
                rolls,
                Optional.ofNullable(content));
    }

    private static NpcShare npcShare(String value) {
        for (NpcShare share : NpcShare.values()) {
            if (share.key().equals(value)) {
                return share;
            }
        }
        throw new UsageException("error.usage.unknown_npc_share", value);
    }

    private static void once(String option, Object current) {
        if (current != null) {
            throw new UsageException("error.usage.duplicate_option", option);
        }
    }

    private static String value(List<String> args, int index, String option) {
        if (index >= args.size() || args.get(index).startsWith("--")) {
            throw new UsageException("error.usage.missing_value", option);
        }
        return args.get(index);
    }

    private static long parseLong(String option, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new UsageException("error.usage.not_a_number", option, value);
        }
    }

    private static int parseInt(String option, String value, int min, int max) {
        long parsed = parseLong(option, value);
        if (parsed < min || parsed > max) {
            throw new UsageException("error.usage.out_of_range", option, value, min, max);
        }
        return (int) parsed;
    }
}
