package kolo.engine.state;

/** Хто віддає накази державі (GD §14). Рушій їх не розрізняє: накази NPC і автопілоту генерує ШІ до ходу. */
public enum ControlType {
    /** Гравець. */
    PLAYER,
    /** NPC-держава. */
    NPC,
    /** Держава гравця, яким тимчасово керує ШІ. */
    AUTOPILOT
}
