package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.TechBranch;

/**
 * Галузь технологій у контенті (GD §4.3, §9.1).
 *
 * @param name назва українською
 */
public record TechBranchDef(TechBranch branch, String name) {

    public TechBranchDef {
        Objects.requireNonNull(branch, "branch");
        Checks.notBlank("tech_branch." + branch.key() + ".name", name);
    }
}
