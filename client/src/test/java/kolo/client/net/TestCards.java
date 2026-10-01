package kolo.client.net;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.HdiLevelId;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.PopulationLevelId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.state.CountryOrigin;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.TechBranch;
import kolo.engine.view.CountryCard;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.WheelKind;

/** Картка держави для тестів клієнта: мала, з одним колесом генерації. */
public final class TestCards {

    public static final CountryCard CARD = card(0);

    private TestCards() {}

    public static CountryCard card(int number) {
        NounPhrase name = new NounPhrase(
                GrammaticalGender.FEMININE,
                List.of("Велор", "Велору", "Велору", "Велор", "Велором", "Велорі", "Велоре"));
        return new CountryCard(
                number,
                new LocalizedName(name, name),
                0,
                1,
                500,
                new IdeologyId("democracy"),
                new SubIdeologyId("liberal_democracy"),
                Optional.empty(),
                List.of(),
                Map.of(
                        TechBranch.ECONOMY,
                        0,
                        TechBranch.MILITARY,
                        0,
                        TechBranch.SOCIETY,
                        0,
                        TechBranch.ENERGY_SCIENCE,
                        0),
                1000,
                60,
                150,
                0,
                NuclearStatus.NONE,
                0,
                0,
                List.of(),
                List.of(),
                new CountryOrigin(
                        new AreaLevelId("medium"),
                        new PopulationLevelId("medium"),
                        new GdpLevelId("middle"),
                        new HdiLevelId("middle"),
                        new ArmySizeId("regular"),
                        List.of(),
                        Optional.empty(),
                        List.of(),
                        PowerCorridor.CLASSIC,
                        100),
                List.of(),
                new TreeSet<>(),
                List.of(new RollRecord(
                        new WheelKind("generation_ideology"),
                        List.of(new RolledSector("democracy", 10_000, OutcomeTier.PARTIAL, 50)),
                        0,
                        List.of(),
                        "democracy",
                        0,
                        0,
                        null)));
    }
}
