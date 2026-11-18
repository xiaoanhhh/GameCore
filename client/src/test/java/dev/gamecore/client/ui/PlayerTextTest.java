package dev.gamecore.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.gamecore.client.registry.GameRegistry;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 验证展示翻译、降级提示、积分符号和实际字体清单，不触及规则/平台权限。 */
class PlayerTextTest {
    @Test void translatesStatesAndResultsWithoutLeakingUnknownValues() {
        assertEquals("比赛就绪", PlayerText.roomState("ALLOCATED"));
        assertEquals("正在比赛", PlayerText.roomState("RUNNING"));
        assertEquals("已中止", PlayerText.roomState("ABORTED"));
        assertEquals("平局", PlayerText.result("DRAW"));
        assertEquals("落败", PlayerText.result("LOSS"));
        assertEquals("玩家 2 获胜", PlayerText.result("PLAYER_TWO"));
        assertEquals("状态已更新", PlayerText.roomState("INTERNAL_STATE"));
        assertEquals("等待结果", PlayerText.result("INTERNAL_RESULT"));
    }

    @Test void rendersSignedPointsAndLocaleIndependentRate() {
        assertEquals("+12 积分", PlayerText.points(12));
        assertEquals("-8 积分", PlayerText.points(-8));
        assertEquals("0 积分", PlayerText.points(0));
        assertEquals("75%", PlayerText.winRate(.75));
    }

    @Test void errorsExplainRecoveryInsteadOfExposingProtocolCodes() {
        assertTrue(PlayerText.error("ROOM_UNAVAILABLE").contains("刷新"));
        assertTrue(PlayerText.error("TICKET_ALREADY_USED").contains("重新进入"));
        assertTrue(PlayerText.error("PASSWORD_LENGTH").contains("72"));
        assertTrue(PlayerText.error("UNAUTHORIZED").contains("重新登录"));
        assertTrue(PlayerText.error("DATABASE_UNAVAILABLE").contains("本地游戏"));
        assertFalse(PlayerText.error("PRIVATE_BACKEND_ERROR").contains("PRIVATE_BACKEND_ERROR"));
    }

    @Test void catalogKeepsStableModesWithPlayerFacingCopy() {
        var registry = GameRegistry.builtIn();
        for (var game : registry.all()) {
            String text = game.summary() + game.description() + game.modes();
            for (String internal : new String[]{"测试", "阶段", "实验", "权威", "v1", "v2", "Bot", "A*"}) {
                assertFalse(text.contains(internal), game.id() + " leaks " + internal);
            }
        }
        assertEquals("直连对战", registry.require("tank").requireMode("online").name());
        assertEquals("直连房间", registry.require("tank").requireMode("multiplayer").name());
    }

    @Test void newPlayerMessagesHavePackagedGlyphs() throws Exception {
        String glyphs;
        try (var input = getClass().getResourceAsStream("/fonts/characters.txt")) {
            assertNotNull(input); glyphs = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String code : new String[]{"INVALID_USERNAME", "INVALID_NAME", "PASSWORD_LENGTH", "ROOM_UNAVAILABLE", "UNAUTHORIZED", "PRIVATE_BACKEND_ERROR"}) {
            PlayerText.error(code).codePoints().filter(c -> !Character.isWhitespace(c)).forEach(c -> assertTrue(glyphs.indexOf(c) >= 0, "Missing glyph " + c));
        }
    }
}
