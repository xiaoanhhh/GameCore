package dev.gamecore.client.traffic;

import static org.junit.jupiter.api.Assertions.*;

import dev.gamecore.platform.api.PlatformMessages.Profile;
import org.junit.jupiter.api.Test;

/** 开关只认平台返回的用户名；未开启时不采集，也不给连接提供旁路。 */
class TrafficMonitorTest {
    private static final Profile DEVELOPER = new Profile("id-1", "testuser", "任意昵称", "USER");
    private final TrafficMonitor monitor = new TrafficMonitor();

    @Test void guestsAndOtherAccountsGetNothing() {
        for (var profile : new Profile[]{null, new Profile("id-2", "player01", "玩家", "USER"), new Profile("id-3", "admin", "管理员", "ADMIN")}) {
            assertFalse(monitor.sync(profile));
            assertFalse(monitor.active());
            assertNull(monitor.observer(profile));
            monitor.exchange("GET", "/api/rooms", 200, "", 1_000_000);
            assertTrue(monitor.log().entries().isEmpty());
        }
    }

    @Test void displayNameAndNearMatchesCannotEnableThePanel() {
        assertFalse(TrafficMonitor.allowed(new Profile("id-2", "player01", "testuser", "USER")));
        assertFalse(TrafficMonitor.allowed(new Profile("testuser", "player01", "玩家", "USER")));
        assertFalse(TrafficMonitor.allowed(new Profile("id-2", "TestUser", "玩家", "USER")), "服务器已转成小写，其他写法不是这个账户");
        assertFalse(TrafficMonitor.allowed(new Profile("id-2", "testuser2", "玩家", "USER")));
        assertFalse(TrafficMonitor.allowed(new Profile("id-2", null, "testuser", "USER")));
    }

    @Test void developerAccountEnablesRecordingAndGetsAConnectionObserver() {
        assertTrue(monitor.sync(DEVELOPER));
        assertTrue(monitor.active());
        assertNotNull(monitor.observer(DEVELOPER));
        var first = monitor.log().entries().getFirst();
        assertEquals(TrafficKind.CONNECTION, first.kind());
        assertTrue(first.text().contains("testuser"));
        assertTrue(monitor.sync(DEVELOPER));
        assertEquals(1, monitor.log().entries().size(), "Repeated sync must not write repeated lines");
    }

    @Test void logoutExpiryOrSwitchingAccountClearsEverything() {
        monitor.sync(DEVELOPER);
        monitor.exchange("GET", "/api/rooms", 200, "", 1_000_000);
        assertFalse(monitor.sync(null));
        assertTrue(monitor.log().entries().isEmpty());
        assertNull(monitor.observer(null));
        monitor.sync(DEVELOPER);
        assertEquals(1, monitor.log().entries().size(), "A new login starts from an empty log");
        assertFalse(monitor.sync(new Profile("id-2", "player01", "玩家", "USER")));
        assertTrue(monitor.log().entries().isEmpty());
    }

    @Test void observerCreatedBeforeLogoutStopsWriting() {
        var observer = monitor.observer(DEVELOPER);
        monitor.sync(null);
        observer.connection("TCP 已断开");
        assertTrue(monitor.log().entries().isEmpty());
    }

    @Test void httpExchangesAreClassifiedByStatus() {
        monitor.sync(DEVELOPER);
        monitor.exchange("GET", "/api/rooms", 200, "", 34_000_000);
        var success = monitor.log().entries().getLast();
        assertEquals(TrafficKind.PLATFORM, success.kind());
        assertEquals(TrafficDirection.ROUND_TRIP, success.direction());
        assertEquals("GET /api/rooms · 200 · 34 ms", success.text());
        monitor.exchange("POST", "/api/rooms", 201, "", 2_000_000);
        assertEquals(TrafficKind.PLATFORM, monitor.log().entries().getLast().kind());
        monitor.exchange("POST", "/api/rooms/r-1/join", 409, "ROOM_FULL", 5_000_000);
        var failure = monitor.log().entries().getLast();
        assertEquals(TrafficKind.ERROR, failure.kind());
        assertEquals("POST /api/rooms/r-1/join · 409 ROOM_FULL · 5 ms", failure.text());
        monitor.exchange("GET", "/api/rooms", 0, "INVALID_RESPONSE", 1_000_000);
        assertTrue(monitor.log().entries().getLast().text().contains("· - INVALID_RESPONSE"));
    }
}
