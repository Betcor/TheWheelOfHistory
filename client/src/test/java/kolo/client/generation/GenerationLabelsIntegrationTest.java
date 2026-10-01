package kolo.client.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kolo.client.TestWorlds;
import kolo.client.i18n.Texts;
import kolo.client.net.GameStart;
import kolo.engine.content.ContentPack;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.state.NpcShare;
import kolo.engine.view.CountryView;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.WheelKind;
import kolo.protocol.message.YearPhase;
import org.junit.jupiter.api.Test;

/**
 * Справжні картки держав з вбудованого сервера: кожне колесо генерації й кожен його сектор клієнт підписує з контенту
 * чи текстів — гравець не бачить сирих id; картка держави теж.
 */
class GenerationLabelsIntegrationTest {

    private static final Texts TEXTS = Texts.ukrainian();
    private static final ContentPack CONTENT = TestWorlds.SERVER.content();

    @Test
    void everyWheelAndSectorOfRealCountriesHasALabel() {
        for (long seed = 1; seed <= 6; seed++) {
            GameStart start = TestWorlds.start(seed, 1 + (int) (seed % 3), NpcShare.values()[(int) (seed % 3)]);
            assertThat(start.phase().phase()).isEqualTo(YearPhase.GENERATION);
            GenerationLabels labels = labels(start);

            assertThat(start.card().rolls()).isNotEmpty();
            for (RollRecord roll : start.card().rolls()) {
                assertThat(labels.knowsWheel(roll.kind())).as(roll.kind().id()).isTrue();
                for (RolledSector sector : roll.sectors()) {
                    assertThat(labels.knowsSector(roll, sector.id()))
                            .as(roll.kind().id() + " / " + sector.id())
                            .isTrue();
                }
            }
            // Назва, що випала, — назва держави.
            start.card().rolls().stream()
                    .filter(roll -> roll.kind().id().equals("generation_name"))
                    .forEach(roll -> assertThat(labels.result(roll))
                            .isEqualTo(start.card().name().fullName().nominative()));
            // Кожен етап показу має колеса.
            assertThat(GenerationPlan.of(start.card().rolls()).stages())
                    .extracting(GenerationPlan.Stage::kind)
                    .containsExactly(GenerationStage.values());
        }
    }

    @Test
    void cardOfARealCountryHasNoRawIds() {
        GameStart start = TestWorlds.start(42, 2, NpcShare.NORMAL);

        List<CountryCardSections.Section> sections =
                CountryCardSections.of(labels(start), TEXTS, start.phase().turn());

        List<String> shown = new ArrayList<>();
        sections.forEach(section -> {
            shown.add(section.title());
            section.lines().forEach(line -> {
                shown.add(line.label());
                shown.add(line.value());
            });
        });
        assertThat(shown).noneMatch(text -> text.contains("_") || text.contains("{"));
        assertThat(sections)
                .extracting(CountryCardSections.Section::title)
                .containsExactly(
                        TEXTS.text("card.section.state"),
                        TEXTS.text("card.section.development"),
                        TEXTS.text("card.section.army"),
                        TEXTS.text("card.section.resources"),
                        TEXTS.text("card.section.history"),
                        TEXTS.text("card.section.people"));
        assertThat(sections.get(4).lines())
                .hasSizeGreaterThanOrEqualTo(start.card().origin().backstory().size());
        assertThat(sections.get(5).lines()).hasSize(start.card().people().size());
    }

    @Test
    void ageOfPeopleOnTheCardIsForTheCurrentYear() {
        GameStart start = TestWorlds.start(42, 2, NpcShare.NORMAL);
        int later = start.phase().turn() + 5;

        List<CountryCardSections.Line> people =
                CountryCardSections.of(labels(start), TEXTS, later).get(5).lines();

        assertThat(start.card().people()).isNotEmpty();
        for (int i = 0; i < people.size(); i++) {
            int age = later - start.card().people().get(i).bornTurn();
            assertThat(people.get(i).value()).contains(", " + age + " р.");
        }
    }

    @Test
    void streakWheelsAreLabeledFromContent() {
        GenerationLabels labels = labels(TestWorlds.start(42, 1, NpcShare.FEW));
        for (StreakKind kind : StreakKind.values()) {
            List<StreakRewardDef> rewards = CONTENT.streaks().wheel(kind).rewards();
            RollRecord roll = new RollRecord(
                    new WheelKind("generation_" + kind.key()),
                    List.of(new RolledSector(rewards.getFirst().id().value(), 10_000, OutcomeTier.PARTIAL, 50)),
                    0,
                    List.of(),
                    rewards.getFirst().id().value(),
                    0,
                    0,
                    null);

            assertThat(labels.wheel(roll.kind()))
                    .contains(CONTENT.streaks().wheel(kind).name());
            assertThat(labels.result(roll)).isEqualTo(rewards.getFirst().name());
            assertThat(GenerationPlan.isKey(roll.kind())).isTrue();
        }
    }

    private static GenerationLabels labels(GameStart start) {
        return new GenerationLabels(
                CONTENT,
                TEXTS,
                start.card(),
                number -> start.map().countries().stream()
                        .filter(country -> country.number() == number)
                        .map(CountryView::name)
                        .findFirst()
                        .or(Optional::empty));
    }
}
