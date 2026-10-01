package kolo.client.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import kolo.client.i18n.Texts;
import kolo.client.net.RemoteLobby;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import org.junit.jupiter.api.Test;

class LobbyLabelsTest {

    private static final Texts TEXTS = Texts.ukrainian();

    @Test
    void remoteLobbyNamesHostPlayersWorldAndAddress() throws Exception {
        InetSocketAddress server =
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {(byte) 192, (byte) 168, 0, 5}), 19_700);
        LobbyInfo lobby =
                new LobbyInfo(3, "a".repeat(32), "Оля", 2, new LobbySetup.NewWorld(42, NpcShare.FEW, TurnTimer.MANUAL));

        assertThat(LobbyLabels.remote(TEXTS, new RemoteLobby(server, lobby)))
                .startsWith("Оля — гравців: 2 · ")
                .contains("42")
                .endsWith(" · 192.168.0.5:19700");
    }

    @Test
    void addressIsWrittenAsTheAddressFieldExpects() throws Exception {
        assertThat(LobbyLabels.address(new InetSocketAddress(InetAddress.getByName("::1"), 4000)))
                .isEqualTo("[0:0:0:0:0:0:0:1]:4000");
        assertThat(LobbyLabels.address(new InetSocketAddress(InetAddress.getLoopbackAddress(), 19_700)))
                .isEqualTo("127.0.0.1:19700");
    }
}
