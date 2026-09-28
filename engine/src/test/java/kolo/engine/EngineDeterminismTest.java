package kolo.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.BaseStream;
import java.util.stream.Collectors;

/**
 * Правила детермінізму рушія: однаковий вхід має давати однаковий вихід на будь-якій ОС і JVM.
 *
 * <p>Що правила справді ловлять порушення, перевіряє {@code EngineDeterminismRulesTest} на фікстурах.
 */
@AnalyzeClasses(packages = "kolo.engine", importOptions = ImportOption.DoNotIncludeTests.class)
public class EngineDeterminismTest {

    private static final String ENGINE = "kolo.engine..";

    @ArchTest
    public static final ArchRule noJdkRandomness = noClasses()
            .that()
            .resideInAPackage(ENGINE)
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(Random.class, ThreadLocalRandom.class, SplittableRandom.class)
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("java.util.random..")
            .orShould()
            .callMethod(Math.class, "random")
            .orShould()
            .callMethod(StrictMath.class, "random")
            .because("єдине джерело випадковості в рушії — kolo.engine.rng.Rng");

    // Не лише годинник: getenv, getProperty, lineSeparator, identityHashCode теж залежать від середовища.
    @ArchTest
    public static final ArchRule noSystemAccess = noClasses()
            .that()
            .resideInAPackage(ENGINE)
            .should()
            .callMethodWhere(DescribedPredicate.describe(
                    "метод java.lang.System, окрім arraycopy",
                    (JavaMethodCall call) -> call.getTargetOwner().isEquivalentTo(System.class)
                            && !call.getName().equals("arraycopy")))
            .because("час, змінні середовища й властивості JVM відрізняються між машинами");

    @ArchTest
    public static final ArchRule noHashOrderedCollections = noClasses()
            .that()
            .resideInAPackage(ENGINE)
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(HashMap.class, HashSet.class, Hashtable.class, IdentityHashMap.class, WeakHashMap.class)
            .orShould()
            .callMethodWhere(DescribedPredicate.describe(
                    "фабрику з невизначеним порядком ітерації (Set.of, Map.of, Collectors.toSet, …)",
                    EngineDeterminismTest::isUnorderedFactory))
            .because("порядок ітерації хеш-колекцій залежить від хешів і версії JDK; "
                    + "дозволені TreeMap, TreeSet, LinkedHashMap і відсортовані List");

    @ArchTest
    public static final ArchRule noConcurrency = noClasses()
            .that()
            .resideInAPackage(ENGINE)
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(Thread.class)
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("java.util.concurrent..")
            .orShould()
            .callMethodWhere(DescribedPredicate.describe(
                    "паралельний потік (parallelStream, parallel)", EngineDeterminismTest::isParallelStream))
            .because("рушій однопотоковий: порядок паралельних обчислень недетермінований");

    @ArchTest
    public static final ArchRule noIo = noClasses()
            .that()
            .resideInAPackage(ENGINE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "java.io..",
                    "java.nio..",
                    "java.net..",
                    "java.time..",
                    "java.util.logging..",
                    "javafx..",
                    "io.netty..",
                    "org.slf4j..")
            .because("рушій — чиста логіка без вводу-виводу й часу; він пише події, а не логи");

    private static boolean isUnorderedFactory(JavaMethodCall call) {
        String name = call.getName();
        int params = call.getTarget().getRawParameterTypes().size();
        if (call.getTargetOwner().isEquivalentTo(Set.class)
                || call.getTargetOwner().isEquivalentTo(java.util.Map.class)) {
            return name.equals("of") || name.equals("ofEntries") || name.equals("copyOf");
        }
        if (call.getTargetOwner().isEquivalentTo(Collectors.class)) {
            return switch (name) {
                case "toSet", "toUnmodifiableSet", "toUnmodifiableMap", "groupingByConcurrent", "toConcurrentMap" ->
                    true;
                // Перевантаження з фабрикою мапи (4 і 3 параметри відповідно) дозволені.
                case "toMap" -> params < 4;
                case "groupingBy" -> params < 3;
                default -> false;
            };
        }
        return false;
    }

    private static boolean isParallelStream(JavaMethodCall call) {
        return call.getName().equals("parallelStream")
                || (call.getName().equals("parallel") && call.getTargetOwner().isAssignableTo(BaseStream.class));
    }
}
