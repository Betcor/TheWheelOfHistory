package kolo.engine.rng;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Інтеграційний тест схеми потоків року: seed світу → seed року → потоки підсистем → потоки фронтів і сезонів.
 *
 * <p>Перевіряє, що в зв'язці {@code mix}, вкладених {@code fork} і {@code nextInt} зміна кількості кидків в
 * одній підсистемі не зсуває результатів в інших — на цьому тримаються реплеї після змін правил.
 */
class RngStreamsIntegrationTest {

    private static final long WORLD_SEED = 20_260_928L;
    private static final String[] SEASONS = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};

    @Test
    void extraRollsInOneSubsystemDoNotShiftOthers() {
        List<Integer> baseline = simulateYear(5, 0);
        List<Integer> withExtraEconomyRolls = simulateYear(5, 17);

        // Перші 3 значення — економіка (зсунута навмисно), решта — війна й глобальні події.
        assertThat(withExtraEconomyRolls.subList(3, baseline.size())).isEqualTo(baseline.subList(3, baseline.size()));
    }

    @Test
    void wholeYearIsReproducible() {
        assertThat(simulateYear(12, 0)).isEqualTo(simulateYear(12, 0));
    }

    @Test
    void differentYearsDiffer() {
        assertThat(simulateYear(12, 0)).isNotEqualTo(simulateYear(13, 0));
    }

    @Test
    void seasonStreamsOfOneFrontAreIndependent() {
        Rng war = Rng.of(Rng.mix(WORLD_SEED, 3)).fork("war:frn_4");

        List<Integer> spring = draw(war.fork(SEASONS[0]), 20);
        List<Integer> springAfterOtherSeasons = new ArrayList<>();
        draw(war.fork(SEASONS[1]), 1_000);
        springAfterOtherSeasons.addAll(draw(war.fork(SEASONS[0]), 20));

        assertThat(springAfterOtherSeasons).isEqualTo(spring);
    }

    /** Імітує кидки одного року: економіка кількох держав, війна по сезонах, глобальна подія. */
    private static List<Integer> simulateYear(int turn, int extraEconomyRolls) {
        Rng year = Rng.of(Rng.mix(WORLD_SEED, turn));
        List<Integer> results = new ArrayList<>();

        Rng economy = year.fork("economy:cty_1");
        for (int i = 0; i < extraEconomyRolls; i++) {
            economy.nextInt(10_000);
        }
        results.addAll(draw(economy, 3));

        for (String front : new String[] {"frn_2", "frn_9"}) {
            Rng war = year.fork("war:" + front);
            for (String season : SEASONS) {
                results.addAll(draw(war.fork(season), 4));
            }
        }

        Rng global = year.fork("globalevent");
        results.add(global.nextInt(10_000));
        results.add(3 + global.nextInt(8));
        return results;
    }

    private static List<Integer> draw(Rng rng, int count) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(rng.nextInt(10_000));
        }
        return values;
    }
}
