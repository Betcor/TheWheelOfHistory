package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.PlayerToken;
import kolo.protocol.message.ServerMessage;
import kolo.server.TestServers;
import kolo.server.auth.PlayerTokens;
import kolo.server.persistence.PlayerRecord;
import kolo.server.persistence.SavedPlayer;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Сесія із завантаженого світу: місце з токеном, гість і роздача місць хостом, продовження з останнього року, помилки
 * завантаження й зайнятість файлу.
 */
@Timeout(60)
class SessionActorLoadTest {

    private static final String WORLD = "world-11";
    private static final LobbySetup SAVED = new LobbySetup.SavedWorld(WORLD, 11, 2, TurnTimer.MANUAL);

    @TempDir
    Path dir;

    private WorldDirectory worlds;
    private long nextId = 1;
    /** Гравці збереженого світу: «Оля» — хост, «Ігор». */
    private ServerMessage.Joined olya;

    private ServerMessage.Joined ihor;

    @BeforeEach
    void saveAWorld() throws Exception {
        worlds = new WorldDirectory(dir);
        RecordingPeer host = new RecordingPeer();
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = session();
        session.open(host, "Оля", 11, NpcShare.FEW);
        olya = host.joined();
        host.lobby();
        session.join(guest, "Ігор");
        ihor = guest.joined();
        guest.lobby();
        host.lobby();
        session.start(host);
        for (RecordingPeer peer : List.of(host, guest)) {
            peer.map();
            peer.expectOrders(0);
        }
        session.leave(guest);
        host.players();
        for (int turn = 0; turn < 2; turn++) {
            session.ready(host, turn);
            host.expectYear(turn);
        }
        session.leave(host);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void playerWithATokenSitsOnTheSeatAndTheYearGoesOn() throws Exception {
        RecordingPeer host = new RecordingPeer();
        SessionActor session = session();

        session.load(host, WORLD, "Хтось", Optional.of(seat(olya)), false);

        ServerMessage.Joined joined = host.joined();
        assertThat(joined.world()).isEqualTo(olya.world());
        assertThat(joined.player()).isEqualTo(1);
        assertThat(joined.token()).isEqualTo(olya.token());
        assertThat(host.lobby())
                .isEqualTo(new ServerMessage.Lobby(
                        session.id(),
                        olya.world(),
                        SAVED,
                        List.of(
                                new PlayerInfo(1, "Оля", true, true, false, OptionalInt.of(0)),
                                new PlayerInfo(2, "Ігор", false, false, false, OptionalInt.of(1))),
                        TestServers.TIMERS));
        assertThat(session.lobby()).contains(new LobbyInfo(session.id(), olya.world(), "Оля", 1, SAVED));

        session.start(host);

        assertThat(host.map()).isEqualTo(TestServers.map(11, 2, NpcShare.FEW));
        host.expectOrders(2);
        session.ready(host, 2);
        host.expectYear(2);
        leaveAndAwait(session, host);
        try (WorldStore store = WorldStore.open(dir.resolve(WORLD + WorldStore.EXTENSION))) {
            assertThat(store.lastTurn()).isEqualTo(3);
        }
    }

    @Test
    void guestIsSeatedByTheHostWithANewToken() throws Exception {
        RecordingPeer host = new RecordingPeer();
        SessionActor session = session();
        session.load(host, WORLD, "Марко", Optional.empty(), true);
        assertThat(host.joined().player()).isEqualTo(3);
        assertThat(host.lobby().players())
                .containsExactly(
                        new PlayerInfo(1, "Оля", false, false, false, OptionalInt.of(0)),
                        new PlayerInfo(2, "Ігор", false, false, false, OptionalInt.of(1)),
                        new PlayerInfo(3, "Марко", true, true, false, OptionalInt.empty()));

        session.assign(host, 3, 1);

        ServerMessage.Joined seated = host.joined();
        assertThat(seated.player()).isEqualTo(1);
        assertThat(seated.world()).isEqualTo(olya.world());
        assertThat(seated.token()).isNotEqualTo(olya.token());
        assertThat(host.lobby().players())
                .containsExactly(
                        new PlayerInfo(1, "Марко", true, true, false, OptionalInt.of(0)),
                        new PlayerInfo(2, "Ігор", false, false, false, OptionalInt.of(1)));
        // Старий токен місця більше не діє.
        RecordingPeer old = new RecordingPeer();
        session.rejoin(old, 1, olya.token());
        assertThat(old.error().code()).isEqualTo(ErrorCode.UNAUTHORIZED);

        session.start(host);
        host.map();
        host.expectOrders(2);
        leaveAndAwait(session, host);
        try (WorldStore store = WorldStore.open(dir.resolve(WORLD + WorldStore.EXTENSION))) {
            assertThat(store.players().stream().map(SavedPlayer::player))
                    .containsExactly(
                            new PlayerRecord(1, "Марко", PlayerTokens.hash(seated.token()), 0, true),
                            new PlayerRecord(2, "Ігор", PlayerTokens.hash(ihor.token()), 1, false));
        }
    }

    @Test
    void untrustedClientWithoutATokenCannotLoadAndTheWorldIsReleased() throws Exception {
        RecordingPeer stranger = new RecordingPeer();
        SessionActor session = session();

        session.load(stranger, WORLD, "Чужий", Optional.empty(), false);

        assertThat(stranger.error().code()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(worlds.inUse(dir.resolve(WORLD + WorldStore.EXTENSION))).isFalse();
        RecordingPeer owner = new RecordingPeer();
        session().load(owner, WORLD, "Оля", Optional.of(seat(olya)), false);
        assertThat(owner.joined().player()).isEqualTo(1);
    }

    @Test
    void wrongSeatTokenIsUnauthorizedEvenForTrustedClients() throws Exception {
        RecordingPeer host = new RecordingPeer();
        SessionActor session = session();

        session.load(host, WORLD, "Оля", Optional.of(new PlayerToken(1, ihor.token())), true);

        assertThat(host.error().code()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void worldOpenInAnotherSessionOrMissingIsReported() throws Exception {
        RecordingPeer first = new RecordingPeer();
        session().load(first, WORLD, "Оля", Optional.of(seat(olya)), false);
        first.joined();
        RecordingPeer second = new RecordingPeer();
        RecordingPeer third = new RecordingPeer();

        session().load(second, WORLD, "Ігор", Optional.of(seat(ihor)), false);
        session().load(third, "world-99", "Ігор", Optional.empty(), true);

        ServerMessage.Error inUse = second.error();
        assertThat(inUse.code()).isEqualTo(ErrorCode.WORLD_IN_USE);
        assertThat(inUse.details()).containsEntry("world", WORLD);
        ServerMessage.Error missing = third.error();
        assertThat(missing.code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(missing.details()).containsEntry("what", "world").containsEntry("id", "world-99");
    }

    @Test
    void worldOfOtherContentIsRejected() throws Exception {
        Path file = dir.resolve(WORLD + WorldStore.EXTENSION);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                Statement statement = connection.createStatement()) {
            statement.execute("UPDATE world_meta SET content_hash = 'other'");
        }
        RecordingPeer host = new RecordingPeer();

        session().load(host, WORLD, "Оля", Optional.of(seat(olya)), false);

        ServerMessage.Error error = host.error();
        assertThat(error.code()).isEqualTo(ErrorCode.SAVE_CONTENT_MISMATCH);
        assertThat(error.details())
                .containsEntry("world", WORLD)
                .containsEntry("saved", "other")
                .containsEntry("current", TestServers.CONTENT.hash());
    }

    @Test
    void gameWaitsUntilEveryGuestHasASeat() throws Exception {
        RecordingPeer host = new RecordingPeer();
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = loadedBy(host);
        session.join(guest, "Петро");
        assertThat(guest.joined().player()).isEqualTo(3);
        guest.lobby();
        host.lobby();

        session.start(host);

        assertThat(host.error().code()).isEqualTo(ErrorCode.PLAYERS_UNSEATED);
        session.assign(host, 3, 2);
        assertThat(guest.joined().player()).isEqualTo(2);
        assertThat(host.lobby().players())
                .containsExactly(
                        new PlayerInfo(1, "Оля", true, true, false, OptionalInt.of(0)),
                        new PlayerInfo(2, "Петро", false, true, false, OptionalInt.of(1)));
        guest.lobby();
        session.start(host);
        guest.map();
        guest.expectOrders(2);
        session.close();
    }

    @Test
    void onlyTheHostAssignsFreeSeats() throws Exception {
        RecordingPeer host = new RecordingPeer();
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = loadedBy(host);
        session.join(guest, "ігор");
        guest.joined();
        guest.lobby();
        host.lobby();

        session.assign(guest, 3, 2);
        session.assign(host, 3, 1);
        session.assign(host, 9, 2);
        session.assign(host, 3, 7);
        session.assign(host, 3, 2);

        assertThat(guest.error().code()).isEqualTo(ErrorCode.FORBIDDEN);
        ServerMessage.Error taken = host.error();
        assertThat(taken.code()).isEqualTo(ErrorCode.SEAT_TAKEN);
        assertThat(taken.details()).containsEntry("player", 1L);
        assertThat(host.error().details()).containsEntry("what", "player").containsEntry("id", 9L);
        assertThat(host.error().details()).containsEntry("what", "seat").containsEntry("id", 7L);
        // Гість «ігор» сідає на місце «Ігор» — той самий гравець без токена.
        assertThat(guest.joined().player()).isEqualTo(2);
        session.close();
    }

    @Test
    void guestCannotTakeTheNicknameOfAnotherPlayerOfTheWorld() throws Exception {
        RecordingPeer host = new RecordingPeer();
        SessionActor session = session();
        session.load(host, WORLD, "ігор", Optional.empty(), true);
        host.joined();
        host.lobby();

        session.assign(host, 3, 1);

        ServerMessage.Error error = host.error();
        assertThat(error.code()).isEqualTo(ErrorCode.NICKNAME_TAKEN);
        assertThat(error.details()).containsEntry("nickname", "ігор");
        session.close();
    }

    @Test
    void playerWithATokenRejoinsTheLobbyAndLeavingFreesTheSeat() throws Exception {
        RecordingPeer host = new RecordingPeer();
        RecordingPeer guest = new RecordingPeer();
        SessionActor session = loadedBy(host);

        session.rejoin(guest, ihor.player(), ihor.token());

        assertThat(guest.joined())
                .isEqualTo(new ServerMessage.Joined(session.id(), ihor.world(), ihor.player(), ihor.token()));
        PlayerInfo back = new PlayerInfo(2, "Ігор", false, true, false, OptionalInt.of(1));
        assertThat(guest.lobby().players()).contains(back);
        assertThat(host.lobby().players()).contains(back);

        session.leave(host);

        // Місце хоста лишилося вільним, хостом став Ігор.
        assertThat(guest.lobby().players())
                .containsExactly(
                        new PlayerInfo(1, "Оля", false, false, false, OptionalInt.of(0)),
                        new PlayerInfo(2, "Ігор", true, true, false, OptionalInt.of(1)));
        session.start(guest);
        guest.map();
        guest.expectOrders(2);
        leaveAndAwait(session, guest);
    }

    @Test
    void createdWorldIsInUseWhileItsSessionIsOpen() throws Exception {
        RecordingPeer host = new RecordingPeer();
        SessionActor session = session();
        session.open(host, "Оля", 12, NpcShare.FEW);
        host.joined();
        host.lobby();
        session.start(host);
        host.map();
        host.expectOrders(0);
        Path file = dir.resolve("world-12" + WorldStore.EXTENSION);

        assertThat(worlds.inUse(file)).isTrue();
        leaveAndAwait(session, host);
        assertThat(worlds.inUse(file)).isFalse();
    }

    // ---- Допоміжне ----

    private SessionActor session() {
        return new SessionActor(
                nextId++,
                TestServers.CONTENT,
                worlds,
                new PlayerTokens(),
                SessionActor.engine(TestServers.CONTENT),
                s -> {},
                new ManualClock());
    }

    /** Лобі збереженого світу, у якому хост «Оля» сів на своє місце. */
    private SessionActor loadedBy(RecordingPeer host) throws InterruptedException {
        SessionActor session = session();
        session.load(host, WORLD, "Оля", Optional.of(seat(olya)), false);
        host.joined();
        host.lobby();
        return session;
    }

    private static PlayerToken seat(ServerMessage.Joined joined) {
        return new PlayerToken(joined.player(), joined.token());
    }

    private static void leaveAndAwait(SessionActor session, RecordingPeer last) throws InterruptedException {
        session.leave(last);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
    }
}
