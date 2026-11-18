package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 验证大厅主题隔离和文字可读性；切角表面、真实控件和资源释放由桌面图形冒烟覆盖。 */
class LobbyThemeTest {
    @Test void lobbyThemeIsScopedAndDoesNotRecolorCombatTheme(){
        var combat=new ThemeManager(null);var lobby=combat.lobby();
        assertFalse(combat.isLobby());assertTrue(lobby.isLobby());assertSame(lobby,lobby.lobby());
        assertEquals(ThemeManager.MINT,combat.accent("snake"));assertEquals(ThemeManager.BLUE,combat.accent("fight"));
        assertEquals(LobbyPalette.TEAL,lobby.accent("snake"));assertEquals(LobbyPalette.AMBER,lobby.accent("tank"));assertEquals(LobbyPalette.CORAL,lobby.accent("fight"));
        // 语义色映射只发生在大厅主题；竞技主题原样返回，Color 单例不被修改。
        assertEquals(LobbyPalette.SIGNAL,lobby.color(ThemeManager.BLUE));assertSame(ThemeManager.BLUE,combat.color(ThemeManager.BLUE));
        assertEquals(Color.valueOf("6ddcff"),ThemeManager.BLUE);
    }
    @Test void copyAndAccentsStayReadableOnEverySurface(){
        Color[] surfaces={LobbyPalette.INK,LobbyPalette.PANEL,LobbyPalette.RAISED,LobbyPalette.TOPBAR};
        for(var text:new Color[]{LobbyPalette.TEXT,LobbyPalette.MUTED,LobbyPalette.SIGNAL,LobbyPalette.TEAL,LobbyPalette.AMBER,LobbyPalette.CORAL}){
            for(var surface:surfaces){assertTrue(contrast(text,surface)>=4.5,text+" on "+surface);}
        }
        assertTrue(contrast(LobbyPalette.DISABLED,Color.valueOf("12161d"))>=4.5,"disabled label");
    }
    @Test void primaryButtonsCarryInkTextAndHairlinesStaySubtle(){
        for(var fill:new Color[]{LobbyPalette.SIGNAL,LobbyPalette.SIGNAL_HI,LobbyPalette.SIGNAL_LO}){assertTrue(contrast(LobbyPalette.INK,fill)>=7,fill.toString());}
        assertTrue(contrast(LobbyPalette.LINE,LobbyPalette.PANEL)<1.5,"hairline border must stay subtle against panels");
        assertTrue(contrast(LobbyPalette.LINE_STRONG,LobbyPalette.INK)>1.6,"secondary button edge must remain visible");
    }
    private static double linear(float v){return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    private static double light(Color c){return .2126*linear(c.r)+.7152*linear(c.g)+.0722*linear(c.b);}
    private static double contrast(Color a,Color b){double x=light(a),y=light(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
}
