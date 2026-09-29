package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Колесо стріку генерації (GD §4.10): «Золота доба» або «Андердог».
 *
 * @param name назва українською
 * @param description опис для гравця
 * @param tags мітки, які держава отримує за будь-якої нагороди, напр. {@code world_attention} для «Золотої доби»
 * @param rewards нагороди в порядку контенту — порядок секторів колеса; хоча б одна, id не повторюються
 */
public record StreakWheelDef(
        StreakKind kind, String name, String description, List<String> tags, List<StreakRewardDef> rewards) {

    public StreakWheelDef {
        Objects.requireNonNull(kind, "kind");
        String field = "streak." + kind.key();
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        tags = Defs.tags(field + ".tags", tags);
        rewards = List.copyOf(rewards);
        if (rewards.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field + ".rewards"));
        }
        Defs.uniqueAll(
                field + ".rewards", rewards.stream().map(StreakRewardDef::id).toList());
    }

    public Optional<StreakRewardDef> reward(StreakRewardId id) {
        Objects.requireNonNull(id, "id");
        return rewards.stream().filter(reward -> reward.id().equals(id)).findFirst();
    }
}
