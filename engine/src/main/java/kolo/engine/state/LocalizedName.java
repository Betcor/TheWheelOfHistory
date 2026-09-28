package kolo.engine.state;

import java.util.Objects;

/**
 * Назва держави чи людини з формами відмінків: генератор видає всі форми одразу, тож шаблонам хроніки не треба
 * відмінювати на льоту (GD §19.1).
 *
 * @param fullName повна назва: «Народна Республіка Велор»
 * @param shortName коротка, для частого вжитку: «Велор»; рід може відрізнятися від повної
 */
public record LocalizedName(NounPhrase fullName, NounPhrase shortName) {

    public LocalizedName {
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(shortName, "shortName");
    }

    @Override
    public String toString() {
        return fullName.nominative();
    }
}
