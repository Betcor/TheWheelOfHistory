package kolo.engine.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorCodeTest {

    @Test
    void keysAreUniqueSnakeCaseWithPrefix() {
        TreeSet<String> keys = new TreeSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.key()).matches("error\\.[a-z][a-z0-9_]*");
            assertThat(keys.add(code.key())).as(code.key()).isTrue();
        }
        assertThat(ErrorCode.INSUFFICIENT_FUNDS.key()).isEqualTo("error.insufficient_funds");
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeCanBeThrownByItsException(ErrorCode code) {
        GameException e = Errors.create(code, ErrorDetails.of("id", "cty_1"));

        assertThat(e).isExactlyInstanceOf(code.exceptionType());
        assertThat(e.code()).isEqualTo(code);
        assertThat(e.details()).containsEntry("id", "cty_1");
    }

    /** Структура ієрархії з архітектурного опису; зміна гілок ламає обробку помилок на клієнті. */
    @Test
    void hierarchyMatchesDesign() {
        assertParent(GameException.class, RuntimeException.class);
        for (Class<?> branch : List.of(
                ValidationException.class,
                RuleViolationException.class,
                NotFoundException.class,
                AuthException.class,
                ConflictException.class,
                ProtocolException.class,
                ContentException.class,
                SaveFileException.class,
                InvariantViolationException.class)) {
            assertParent(branch, GameException.class);
        }
        assertParent(InvalidOrderException.class, ValidationException.class);
        for (Class<?> rule : List.of(
                InsufficientFundsException.class,
                InsufficientResourcesException.class,
                PrerequisiteMissingException.class,
                NotOwnerException.class,
                PhaseClosedException.class,
                VassalRestrictionException.class,
                DiplomaticRestrictionException.class,
                FateTokenLimitException.class)) {
            assertParent(rule, RuleViolationException.class);
        }
        assertParent(UnauthorizedException.class, AuthException.class);
        assertParent(ForbiddenException.class, AuthException.class);
        assertParent(VersionMismatchException.class, ProtocolException.class);
        assertParent(SaveVersionException.class, SaveFileException.class);
    }

    private static void assertParent(Class<?> type, Class<?> parent) {
        assertThat(type.getSuperclass()).as(type.getSimpleName()).isEqualTo(parent);
    }
}
