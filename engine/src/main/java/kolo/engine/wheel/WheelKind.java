package kolo.engine.wheel;

/**
 * Тип колеса, напр. {@code construction}, {@code economic_cycle}, {@code generation_ideology}.
 *
 * <p>Не enum: типи коліс задає контент. Ключ — {@code snake_case}.
 */
public record WheelKind(String id) implements Comparable<WheelKind> {

    public WheelKind {
        Ids.requireSnakeCase(id, "тип колеса");
    }

    @Override
    public int compareTo(WheelKind other) {
        return id.compareTo(other.id);
    }
}
