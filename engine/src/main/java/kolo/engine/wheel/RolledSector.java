package kolo.engine.wheel;

/** Сектор у записі обертання: фінальна вага після переваги й нормалізації. */
public record RolledSector(String id, int weightBp, OutcomeTier tier, int quality) {}
