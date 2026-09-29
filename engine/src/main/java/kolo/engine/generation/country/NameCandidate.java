package kolo.engine.generation.country;

import java.util.Objects;
import kolo.engine.content.NameStyleId;
import kolo.engine.state.LocalizedName;

/**
 * Назва-кандидат на колесі назви ({@link NameWheel}).
 *
 * @param style мовний стиль кореня: якщо назва випаде, ним звуть людей держави
 * @param name назва в усіх відмінках
 */
public record NameCandidate(NameStyleId style, LocalizedName name) {

    public NameCandidate {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(name, "name");
    }
}
