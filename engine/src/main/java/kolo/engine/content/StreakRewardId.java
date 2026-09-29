package kolo.engine.content;

import kolo.engine.error.Checks;

/** Ключ нагороди колеса стріку в контенті ({@code snake_case}); унікальний у межах колеса. */
public record StreakRewardId(String value) implements Comparable<StreakRewardId> {

    public StreakRewardId {
        Checks.snakeCase("streak_reward_id", value);
    }

    @Override
    public int compareTo(StreakRewardId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
