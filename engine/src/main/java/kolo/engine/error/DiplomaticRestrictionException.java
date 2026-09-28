package kolo.engine.error;

import java.util.Map;

/** Дія заборонена чинною угодою чи дипломатичним станом. */
public class DiplomaticRestrictionException extends RuleViolationException {

    private static final long serialVersionUID = 1L;

    public DiplomaticRestrictionException(Map<String, ?> details) {
        super(ErrorCode.DIPLOMATIC_RESTRICTION, details);
    }
}
