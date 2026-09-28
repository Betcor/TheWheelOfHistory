package kolo.engine.archfixture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** Навмисні порушення детермінізму — по одному на кожне правило. Лише для тестів правил. */
public final class Violations {

    private Violations() {}

    public static final class UsesJdkRandom {
        public static int roll() {
            return new Random().nextInt(10);
        }
    }

    public static final class UsesMathRandom {
        public static double roll() {
            return Math.random();
        }
    }

    public static final class UsesClock {
        public static long now() {
            return System.currentTimeMillis();
        }
    }

    public static final class UsesEnvironment {
        public static String home() {
            return System.getenv("HOME");
        }
    }

    public static final class UsesHashMap {
        public static Map<String, Integer> map() {
            return new HashMap<>();
        }
    }

    public static final class UsesSetOf {
        public static Set<String> set() {
            return Set.of("a", "b");
        }
    }

    public static final class UsesCollectorsToSet {
        public static Set<String> set(List<String> values) {
            return values.stream().collect(Collectors.toSet());
        }
    }

    public static final class UsesParallelStream {
        public static int sum(List<Integer> values) {
            return values.parallelStream().mapToInt(Integer::intValue).sum();
        }
    }

    public static final class UsesCompletableFuture {
        public static CompletableFuture<Integer> async() {
            return CompletableFuture.completedFuture(1);
        }
    }

    public static final class UsesJavaTime {
        public static java.time.Year year() {
            return java.time.Year.of(1970);
        }
    }
}
