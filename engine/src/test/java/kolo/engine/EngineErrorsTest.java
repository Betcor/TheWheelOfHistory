package kolo.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import kolo.engine.error.GameException;

/**
 * Правила помилок рушія: усе, що рушій кидає як помилку гри, — {@link GameException} з кодом, а не стандартні
 * винятки без коду, які клієнт не зміг би показати гравцеві.
 *
 * <p>Що правила ловлять порушення, перевіряє {@code EngineErrorsRulesTest} на фікстурах.
 */
@AnalyzeClasses(packages = "kolo.engine", importOptions = ImportOption.DoNotIncludeTests.class)
public class EngineErrorsTest {

    private static final List<Class<? extends RuntimeException>> UNCODED = List.of(
            IllegalArgumentException.class,
            IllegalStateException.class,
            UnsupportedOperationException.class,
            RuntimeException.class);

    // Пакет error — виняток: там перевіряються помилки програміста в самій ієрархії (невідповідний код).
    // NullPointerException з Objects.requireNonNull лишається дозволеним: null у моделі — завжди баг виклику.
    @ArchTest
    public static final ArchRule noUncodedExceptions = noClasses()
            .that()
            .resideInAPackage("kolo.engine..")
            .and()
            .resideOutsideOfPackage("kolo.engine.error..")
            .should()
            .callConstructorWhere(DescribedPredicate.describe(
                    "конструктор стандартного винятку без коду помилки",
                    (JavaConstructorCall call) -> UNCODED.stream()
                            .anyMatch(type -> call.getTargetOwner().isEquivalentTo(type))))
            .because("помилки рушія — GameException з ErrorCode (ключ i18n) і подробицями");

    @ArchTest
    public static final ArchRule gameExceptionsLiveInErrorPackage = classes()
            .that()
            .areAssignableTo(GameException.class)
            .should()
            .resideInAPackage("kolo.engine.error")
            .because("ієрархія винятків спільна для сервера й клієнта й описана в одному місці");
}
