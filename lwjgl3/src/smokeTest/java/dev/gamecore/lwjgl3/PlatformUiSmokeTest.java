package dev.gamecore.lwjgl3;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.sun.net.httpserver.HttpServer;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.screen.*;
import dev.gamecore.platform.api.PlatformMessages.Ticket;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** 回环 HTTP 数据驱动真实控件：验证长文案、房间操作、好友/战绩、缩放与自动刷新焦点。 */
public final class PlatformUiSmokeTest {
    private PlatformUiSmokeTest() { }
    private static final String ROOM = "r-" + "a".repeat(32), USER = "player01";
    private static final String LONG_NAME = "快乐玩家".repeat(8);
    private static final String GAME = "{\"id\":\"%s\",\"version\":\"1\",\"rulesVersion\":\"%s/1\",\"players\":2,\"tps\":30}";
    private static final String GAMES = "[" + String.format(GAME,"snake","snake") + "," + String.format(GAME,"tank","tank") + "," + String.format(GAME,"fight","fight") + "]";
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]).toAbsolutePath(); Files.createDirectories(out);
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
        Fixture fixture = new Fixture(); http.createContext("/api/", exchange -> {
            String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod(), body;
            if (path.equals("/api/public/lobby")) {
                body = "{\"games\":" + GAMES + ",\"rooms\":[" +
                        "{\"id\":\"" + ROOM + "\",\"gameId\":\"snake\",\"status\":\"OPEN\",\"players\":1,\"capacity\":2,\"readyPlayers\":0}," +
                        "{\"id\":\"r-" + "b".repeat(32) + "\",\"gameId\":\"tank\",\"status\":\"OPEN\",\"players\":2,\"capacity\":2,\"readyPlayers\":1}," +
                        "{\"id\":\"r-" + "c".repeat(32) + "\",\"gameId\":\"fight\",\"status\":\"ALLOCATED\",\"players\":2,\"capacity\":2,\"readyPlayers\":2}]}";
            } else if (path.equals("/api/auth/login")) {
                body = "{\"token\":\"test-only\",\"expiresAt\":1,\"profile\":" + fixture.profile() + "}";
            } else if (path.equals("/api/games")) { body = GAMES; }
            else if (path.equals("/api/rooms")) { body = "[" + fixture.room() + "]"; }
            else if (path.endsWith("/ready")) { fixture.ready = true; body = fixture.room(); }
            else if (path.endsWith("/leave")) { fixture.left = true; body = "{}"; }
            else if (path.equals("/api/me") && method.equals("PUT")) { fixture.renamed = true; body = fixture.profile(); }
            else if (path.equals("/api/friends")) { body = "[{\"userId\":\"friend\",\"username\":\"friend01\",\"displayName\":\"" + LONG_NAME + "\",\"status\":\"ACCEPTED\",\"incoming\":false,\"online\":true}]"; }
            else if (path.equals("/api/invitations")) { body = "[{\"roomId\":\"invite-room\",\"fromUser\":\"INTERNAL_ACCOUNT_ID\",\"gameId\":\"fight\",\"expiresAt\":1}]"; }
            else if (path.equals("/api/history")) { body = "[{\"id\":\"match\",\"gameId\":\"tank\",\"status\":\"FINISHED\",\"outcome\":\"PLAYER_TWO\",\"tick\":100,\"endedAt\":1,\"participants\":[{\"userId\":\"" + USER + "\",\"displayName\":\"玩家\",\"slot\":0,\"points\":-8,\"result\":\"LOSS\"}]}]"; }
            else if (path.startsWith("/api/ranks/")) { body = "[{\"userId\":\"" + USER + "\",\"displayName\":\"" + LONG_NAME + "\",\"games\":4,\"wins\":3,\"winRate\":0.75,\"points\":12}]"; }
            else { body = "{}"; }
            exchange.getRequestBody().close(); byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length); try (var stream = exchange.getResponseBody()) { stream.write(bytes); } exchange.close();
        }); http.start();
        System.setProperty("gamecore.platform.url","http://127.0.0.1:"+http.getAddress().getPort());
        System.setProperty("gamecore.offline","false");
        Probe client = new Probe(out);
        var configuration = Lwjgl3Launcher.configuration(DesktopSettings.defaults()); configuration.disableAudio(true);
        configuration.setTitle("GameCore platform UI acceptance");
        try {
            new Lwjgl3Application(client,configuration); client.platform().termination().get(8,TimeUnit.SECONDS);
            require(client.done && client.isDisposed(),"Incomplete UI acceptance");
        } finally { http.stop(0); System.clearProperty("gamecore.platform.url"); System.clearProperty("gamecore.offline"); }
        Files.writeString(out.resolve("result.txt"),"PLATFORM_UI_OK\nGuestCardsAndDisabledRooms=true\nRefreshScrollAndFocus=true\nLoginAndCurrentRoom=true\nReadyTransition=true\nLongNamesAndGlyphs=true\nFriendsAndInvitations=true\nRankingAndNegativePoints=true\nGrowShrinkAndMaximized=true\nProfileRename=true\nTicketViewHidesAddress=true\nHttpTerminated=true\n");
        System.out.println("PLATFORM_UI_OK");
    }
    private static final class Fixture {
        volatile boolean ready, renamed, left;
        String profile() { return "{\"userId\":\"" + USER + "\",\"username\":\"player01\",\"displayName\":\"" + (renamed ? "快乐玩家" : LONG_NAME) + "\",\"role\":\"USER\"}"; }
        String room() { return "{\"id\":\"" + ROOM + "\",\"gameId\":\"snake\",\"hostId\":\"" + USER + "\",\"status\":\"" + (ready ? "ALLOCATED" : "OPEN") + "\",\"members\":[{\"userId\":\"" + USER + "\",\"displayName\":\"" + LONG_NAME + "\",\"slot\":0,\"ready\":" + ready + ",\"online\":true}]}"; }
    }
    private static void require(boolean value,String message) { if (!value) { throw new IllegalStateException(message); } }
    static final class Probe extends GameCoreClient {
        final Path out; final long deadline = System.nanoTime()+100_000_000_000L;
        int phase,frames; long waiting; float scrollY; boolean done;
        Probe(Path out) { super(out.resolve("preferences.properties")); this.out=out; }
        UiScreen ui() { return (UiScreen)getScreen(); }
        Actor actor(String id) { return ui().stage().getRoot().findActor(id); }
        boolean text(String id,String part) { return actor(id) instanceof Label label && label.getText().toString().contains(part); }
        void click(String id) {
            Actor a=actor(id); require(a!=null,"Missing "+id);
            var point=a.localToStageCoordinates(new Vector2(a.getWidth()/2,a.getHeight()/2));
            Actor hit=ui().stage().hit(point.x,point.y,true);
            require(hit==a || hit!=null && hit.isDescendantOf(a),"Unreachable "+id);
            ui().stage().stageToScreenCoordinates(point);
            Gdx.input.getInputProcessor().touchDown(Math.round(point.x),Math.round(point.y),0,Input.Buttons.LEFT);
            Gdx.input.getInputProcessor().touchUp(Math.round(point.x),Math.round(point.y),0,Input.Buttons.LEFT);
        }
        void capture(String name) {
            var pixels=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
            PixmapIO.writePNG(new FileHandle(out.resolve(name).toFile()),pixels,6,true); pixels.dispose();
        }
        void layout(Actor actor) {
            if (!actor.isVisible()) { return; }
            if (actor instanceof Label label) {
                String text=label.getText().toString();
                for (String word : new String[]{"INTERNAL_ACCOUNT_ID", ROOM, "ALLOCATED", "FINISHED", "LOSS", "Tick", "实验", "测试阶段"}) { require(!text.contains(word),"Internal copy exposed: "+word); }
                require(label.getGlyphLayout().width<=label.getWidth()+2,"Text wider than control: "+text);
                require(label.getGlyphLayout().height<=label.getHeight()+2,"Text taller than control: "+text);
                text.codePoints().filter(c->!Character.isWhitespace(c)).forEach(c -> require(label.getStyle().font.getData().hasGlyph((char)c),"Missing glyph "+c));
            }
            if (actor instanceof Group group) { for (Actor child:group.getChildren()) { layout(child); } }
        }
        @Override public void render() {
            super.render(); require(System.nanoTime()<deadline,"UI timeout phase="+phase);
            if (++frames%12!=0) { return; }
            try {
                if (phase==0 && actor("platform-game-fight")!=null) {
                    layout(ui().stage().getRoot()); capture("guest-1280.png");
                    require(((Button)actor("platform-join-r-"+"b".repeat(32))).isDisabled(),"Full room enabled");
                    require(((Button)actor("platform-join-r-"+"c".repeat(32))).isDisabled(),"Allocated room enabled");
                    ui().stage().setKeyboardFocus(actor("platform-join-r-"+"b".repeat(32)));
                    Gdx.input.getInputProcessor().keyDown(Input.Keys.ENTER); Gdx.input.getInputProcessor().keyUp(Input.Keys.ENTER);
                    require(actor("platform-login")==null,"Keyboard bypassed disabled join");
                    ScrollPane scroll=(ScrollPane)actor("platform-scroll"); scroll.setScrollY(scroll.getMaxY()); scroll.updateVisualScroll();
                    ui().stage().setKeyboardFocus(actor("platform-join-"+ROOM)); scrollY=scroll.getScrollY(); waiting=System.nanoTime(); phase++;
                } else if (phase==1 && System.nanoTime()-waiting>4_000_000_000L && text("platform-notice","房间列表已更新")) {
                    require(Math.abs(((ScrollPane)actor("platform-scroll")).getScrollY()-scrollY)<2,"Refresh lost scroll");
                    require(ui().stage().getKeyboardFocus()==actor("platform-join-"+ROOM),"Refresh lost focus");
                    click("platform-tab-登录"); phase++;
                } else if (phase==2 && actor("platform-login")!=null) {
                    layout(ui().stage().getRoot()); capture("login.png");
                    ((TextField)actor("platform-username")).setText("player01"); ((TextField)actor("platform-password")).setText("test-password-only"); click("platform-login"); phase++;
                } else if (phase==3 && actor("platform-ready")!=null) {
                    layout(ui().stage().getRoot()); capture("current-room.png"); click("platform-ready"); phase++;
                } else if (phase==4 && actor("platform-play")!=null) {
                    layout(ui().stage().getRoot()); capture("ready.png"); click("platform-tab-好友"); phase++;
                } else if (phase==5 && actor("platform-invitation-invite-room")!=null) {
                    layout(ui().stage().getRoot()); capture("friends.png"); click("platform-tab-战绩"); phase++;
                } else if (phase==6 && actor("platform-history-match-match")!=null) {
                    require(text("platform-history-match-match","-8 积分"),"Wrong point sign"); layout(ui().stage().getRoot()); capture("history.png");
                    click("platform-rank-fight"); phase++;
                } else if (phase==7 && text("platform-notice","战绩已更新 · 终极格斗")) {
                    Gdx.graphics.setWindowedMode(1920,1080); waiting=System.nanoTime(); phase++;
                } else if (phase==8 && System.nanoTime()-waiting>1_000_000_000L) {
                    layout(ui().stage().getRoot()); capture("history-1920.png"); Gdx.graphics.setWindowedMode(960,540); waiting=System.nanoTime(); phase++;
                } else if (phase==9 && System.nanoTime()-waiting>1_000_000_000L) {
                    layout(ui().stage().getRoot()); capture("history-960.png");
                    ((com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics)Gdx.graphics).getWindow().maximizeWindow(); waiting=System.nanoTime(); phase++;
                } else if (phase==10 && System.nanoTime()-waiting>1_000_000_000L) {
                    layout(ui().stage().getRoot()); capture("history-maximized.png"); click("platform-tab-个人主页"); phase++;
                } else if (phase==11 && actor("platform-name")!=null) {
                    ((TextField)actor("platform-name")).setText("快乐玩家"); click("platform-rename"); phase++;
                } else if (phase==12 && text("platform-notice","昵称已保存")) {
                    layout(ui().stage().getRoot()); capture("profile.png");
                    platformPlay(new Ticket("127.0.0.1",1,ROOM,"snake","1","snake/1","match",0,"test-only",1)); phase++;
                } else if (phase==13 && getScreen() instanceof OnlineArenaScreen) {
                    require(actor("online-host")==null && actor("online-port")==null && actor("online-room")==null,"Ticket view exposed address");
                    capture("automatic-match.png"); click("online-back"); phase++;
                } else if (phase==14 && getScreen() instanceof PlatformScreen && actor("platform-play")!=null) {
                    capture("returned.png"); done=true; Gdx.app.exit();
                }
            } catch (RuntimeException failure) { capture("failure-phase-"+phase+".png"); throw failure; }
        }
    }
}
