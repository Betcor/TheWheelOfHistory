package kolo.engine.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.GridPoint;
import kolo.engine.state.Climate;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;
import org.junit.jupiter.api.Test;

class CellViewTest {

    private static final List<GridPoint> SQUARE =
            List.of(new GridPoint(0, 0), new GridPoint(10, 0), new GridPoint(10, 10), new GridPoint(0, 10));

    static CellView land(OptionalInt country) {
        return new CellView(
                new GridPoint(5, 5),
                SQUARE,
                List.of(),
                CellKind.LAND,
                Optional.of(Terrain.PLAIN),
                Optional.of(Relief.PLAIN),
                Optional.of(Climate.TEMPERATE),
                OptionalInt.of(20),
                OptionalInt.of(50),
                false,
                OptionalInt.empty(),
                country);
    }

    static CellView water(CellKind kind) {
        return new CellView(
                new GridPoint(5, 5),
                SQUARE,
                List.of(),
                kind,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false,
                OptionalInt.empty(),
                OptionalInt.empty());
    }

    @Test
    void landAndWaterAreValid() {
        assertThat(land(OptionalInt.of(0)).isLand()).isTrue();
        assertThat(water(CellKind.SEA).isLand()).isFalse();
        assertThat(water(CellKind.LAKE).isLand()).isFalse();
    }

    @Test
    void waterHasNoLandLayers() {
        assertThatThrownBy(() -> new CellView(
                        new GridPoint(5, 5),
                        SQUARE,
                        List.of(),
                        CellKind.SEA,
                        Optional.of(Terrain.PLAIN),
                        Optional.empty(),
                        Optional.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        false,
                        OptionalInt.empty(),
                        OptionalInt.empty()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void riverNeedsDownstream() {
        assertThatThrownBy(() -> new CellView(
                        new GridPoint(5, 5),
                        SQUARE,
                        List.of(),
                        CellKind.LAND,
                        Optional.of(Terrain.PLAIN),
                        Optional.of(Relief.PLAIN),
                        Optional.of(Climate.TEMPERATE),
                        OptionalInt.of(20),
                        OptionalInt.of(50),
                        true,
                        OptionalInt.empty(),
                        OptionalInt.empty()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void polygonNeedsThreePoints() {
        assertThatThrownBy(() -> new CellView(
                        new GridPoint(5, 5),
                        SQUARE.subList(0, 2),
                        List.of(),
                        CellKind.SEA,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        false,
                        OptionalInt.empty(),
                        OptionalInt.empty()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void mapChecksReferences() {
        NounPhrase phrase = new NounPhrase(GrammaticalGender.FEMININE, List.of("а", "и", "і", "у", "ою", "і", "о"));
        LocalizedName name = new LocalizedName(phrase, phrase);
        assertThat(new MapView(1, 10, 10, List.of(land(OptionalInt.of(0))), List.of(new CountryView(0, name, true, 1)))
                        .cellSide())
                .isEqualTo(10);
        assertThatThrownBy(() -> new MapView(1, 10, 10, List.of(land(OptionalInt.of(1))), List.of()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new MapView(
                        1, 10, 10, List.of(water(CellKind.SEA)), List.of(new CountryView(1, name, true, 1))))
                .isInstanceOf(ValidationException.class);
    }
}
