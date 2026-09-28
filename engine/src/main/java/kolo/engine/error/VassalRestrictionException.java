package kolo.engine.error;

import java.util.Map;

/** Васал не може зробити цю дію. */
public class VassalRestrictionException extends RuleViolationException {

    private static final long serialVersionUID = 1L;

    public VassalRestrictionException(Map<String, ?> details) {
        super(ErrorCode.VASSAL_RESTRICTION, details);
    }
}
