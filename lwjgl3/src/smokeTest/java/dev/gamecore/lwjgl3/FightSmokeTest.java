package dev.gamecore.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.screen.FightScreen;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FighterKind;
import dev.gamecore.fight.FightPunch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 真实窗口从搜索、角色选择到结算的验收；技能只走正式输入管线，不注入生命或能量。 */
public final class FightSmokeTest {
    private FightSmokeTest() { }
    public static void main(String[] args) throws IOException {
        Path report = Path.of(args[0]); Files.createDirectories(report); Files.deleteIfExists(report.resolve("result.txt"));
        Probe probe = new Probe(report); var config = Lwjgl3Launcher.configuration(DesktopSettings.defaults()); config.disableAudio(true);
        new Lwjgl3Application(probe, config);
        require(probe.finished && probe.isDisposed(), "Fight acceptance incomplete");
        require(probe.pages.stream().allMatch(UiScreen::isDisposed), "Stage leaked");
        require(Gdx.input.getInputProcessor() == null, "Input retained after exit"); probe.dispose();
        require(probe.lastDisplay != null && probe.lastDisplay.getRegions().first().getTexture().getTextureObjectHandle() == 0, "Display font leaked after exit");
        require(probe.stageTextures.size()==dev.gamecore.fight.FightMap.values().length&&probe.stageTextures.stream().allMatch(t->t.getTextureObjectHandle()==0),"Selected map textures leaked");
        require(probe.laughTexture!=null&&probe.laughTexture.getTextureObjectHandle()==0,"Laugh texture leaked");
        require(probe.materialTexture!=null&&probe.materialTexture.getTextureObjectHandle()==0,"Scenery material texture leaked");
        Files.writeString(report.resolve("result.txt"), "FIGHT_SMOKE_OK\nCatalogSearch=true\nBothRolesSelectable=true\n"
                + "RitualCasterFocusAndReturn=true\nDiagonalSkyLightningAndDarkness=true\nEightWayWebCaptureInflateBurst=true\nWebDodgeAndNoEarlyDamage=true\nGuardJumpBlinkAndKnockdown=true\nBlinkCooldownAndRealRoof=true\nSpecialPauseAndCleanup=true\nBothSpecialRolesAndDualWeb=true\nIconHudKeysAndEnergy=true\nDistinctRealImpactStyles=true\nImpactPauseAndRoundClear=true\nIconHudSmallAndLargeBounds=true\nFiveMapsSelectable=true\nThemedSceneryAndClimbRoutes=true\nFiveMapCardsInSmallWindow=true\nBellyLaughYAnd9=true\nLaughEightPosesAndFocus=true\nLaughCancelAndVulnerable=true\nLaughPauseAndRelease=true\nLaughTextureReleased=true\nDuelIndependentInput=true\nMapGeometryApplied=true\nMapSwitchResetsWorld=true\nMapTextureCacheReleased=true\nBothWinnersKungFuDance=true\nVictoryPauseAndFrozenRules=true\nOpeningFocusAndGreetings=true\nOpeningFrozenRules=true\nOpeningCountdown321=true\nOpeningPauseAndSkip=true\nBoxingWhiffCycle=true\nBoxingSingleQueue=true\nBoxingPhasedArtLoaded=true\nV2AccelerationAndBrake=true\nV2RunDust=true\nV2DoubleJump=true\nV2PlatformsAndDrop=true\nV2ThreePunchCombo=true\nV2LayeredImpact=true\nV2Cinematic=true\nShortTapSkills=true\nHealthAndEnergyHud=true\nLightningAndWeb=true\n"
                + "UltimateFromRealEnergy=true\nGuardDamageReduction=true\nPunchKnockout=true\nBotFights=true\n"
                + "PauseResumeFocus=true\nSelectorPauses=true\nRestartClears=true\nResize=true\nGlyphCoverage=true\n"
                + "StagesDisposed=true\nInputDetached=true\nDisplayFontsRefreshedAndReleased=true\nOpenGL=" + probe.gl + "\n" + probe.displayMetrics);
        System.out.println("FIGHT_SMOKE_OK");
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }
    private static final class Probe extends GameCoreClient {
        private final Path report; private final List<UiScreen> pages = new ArrayList<>();
        private int frame, step; private boolean finished; private String gl;
        private boolean nativePauseBetweenSteps, expectRunningBetweenSteps;
        private com.badlogic.gdx.graphics.g2d.BitmapFont previousDisplay, lastDisplay;
        private final StringBuilder displayMetrics = new StringBuilder();
        private final List<com.badlogic.gdx.graphics.Texture> stageTextures=new ArrayList<>();
        private com.badlogic.gdx.graphics.Texture laughTexture;
        private com.badlogic.gdx.graphics.Texture materialTexture;
        private Probe(Path report) { super(report.resolve("preferences.properties")); this.report = report; }
        private UiScreen ui() { return (UiScreen) getScreen(); }
        private FightScreen fight() { return (FightScreen) getScreen(); }
        private Actor actor(String name) { return ui().stage().getRoot().findActor(name); }
        @Override public void pause() { super.pause(); nativePauseBetweenSteps = true; }
        @Override public void render() {
            getScreen().render(0); if (!pages.contains(ui())) { pages.add(ui()); }
            if (++frame % 3 != 0) { return; }
            // 原生窗口焦点事件在长脚本返回事件循环后才派发；只在步骤之间用正式P键继续。
            // 每个步骤内部的主动暂停/冻结断言照常执行，生产失焦暂停策略不改。
            if (expectRunningBetweenSteps && nativePauseBetweenSteps && getScreen() instanceof FightScreen
                    && fight().snapshot().state() == SessionState.PAUSED) {
                System.out.println("FIGHT_SMOKE_NATIVE_FOCUS_PAUSE tick=" + fight().snapshot().tick());
                key(Input.Keys.P); require(fight().snapshot().state() == SessionState.RUNNING, "Native focus pause could not resume normally");
            }
            switch (step) {
                case 0 -> {
                    ((TextField) actor("search")).setText("终极格斗"); capture("fight-library.png"); click("game-fight"); step++;
                }
                case 1 -> { capture("fight-detail.png"); click("mode-duel"); click("start-preview"); step++; }
                case 2 -> {
                    require(fight().snapshot().state() == SessionState.READY, "Not ready");
                    require(fight().snapshot().fighters().get(0).kind() == FighterKind.NAIWA, "Wrong P1");
                    for (char glyph : Gdx.files.internal("fonts/characters.txt").readString("UTF-8").toCharArray()) {
                        if (!Character.isWhitespace(glyph)) { require(assets().body.getData().hasGlyph(glyph), "Missing glyph " + glyph); }
                    }
                    verifySelection();
                    capture("duel-ready.png"); capture("v2-stage.png"); capture("v2-1280.png");
                    configure(FighterKind.FEIDUDU,FighterKind.NAIWA,dev.gamecore.fight.FightMap.NEON);
                    require(fight().snapshot().fighters().get(0).kind() == FighterKind.FEIDUDU && fight().snapshot().fighters().get(1).kind() == FighterKind.NAIWA, "Role selection ignored");
                    require(((Label) actor("fight-state")).getText().toString().contains("P1 肥嘟嘟 · 蜘蛛丝"), "Ready description did not follow selected role");
                    capture("roles-swapped.png");
                    configure(FighterKind.NAIWA,FighterKind.FEIDUDU,dev.gamecore.fight.FightMap.NEON);
                    verifyMaps();verifyOpening(); startBattle();verifyLaugh(); verifyBoxing();verifyImpactUi();verifySpecials(); down(Input.Keys.D); down(Input.Keys.LEFT); advance(10); up(Input.Keys.D); up(Input.Keys.LEFT);
                    var stopped = fight().snapshot(); advance(1);
                    require(Math.abs(fight().snapshot().fighters().get(0).vx()) < Math.abs(stopped.fighters().get(0).vx()), "Release did not brake");
                    advance(3); var halted = fight().snapshot(); advance(2);
                    require(fight().snapshot().fighters().get(0).x() == halted.fighters().get(0).x(), "Brake did not settle in four steps");
                    require(stopped.fighters().get(0).x() > 560 && stopped.fighters().get(1).x() < 1680, "Dual input failed");
                    down(Input.Keys.D); pause(); var paused = fight().snapshot(); fight().render(20); require(paused.equals(fight().snapshot()), "Paused simulation advances");
                    key(Input.Keys.P); advance(1); require(paused.fighters().get(0).x() == fight().snapshot().fighters().get(0).x(), "Focus pause retained keys");
                    key(Input.Keys.ESCAPE); capture("duel-paused.png"); click("fight-restart"); startBattle(); step++;
                }
                case 3 -> {
                    key(Input.Keys.U); key(Input.Keys.NUMPAD_4); advance(13);
                    require(fight().snapshot().projectiles().size() == 2, "Both ranged attacks not emitted: " + fight().snapshot()); capture("lightning-and-web.png");
                    advance(85); require(fight().snapshot().fighters().get(0).health() == 94 && fight().snapshot().fighters().get(1).health() == 93, "V2 elemental ranged damage wrong");
                    require(((Label) actor("fight-values-0")).getText().toString().contains("94 / 100"), "Health HUD stale");
                    require(fight().snapshot().fighters().get(0).slowTicks() > 0, "Web slow missing"); capture("web-hit.png");
                    click("fight-restart"); startBattle(); step++;
                }
                case 4 -> {
                    advance(710); capture("v2-hud-full-energy.png"); key(Input.Keys.I); advance(21);
                    require(fight().snapshot().ultimates().stream().anyMatch(u->u.kind()==FighterKind.NAIWA), "Lightning ritual not emitted");
                    require(fight().snapshot().fighters().get(0).energy() < 4, "Ultimate cost wrong"); capture("lightning-ultimate.png"); capture("v2-ultimate-cinematic.png");
                    advance(140); require(fight().snapshot().fighters().get(1).health() == 80, "Ultimate damage wrong");
                    click("fight-restart"); startBattle(); advance(710); key(Input.Keys.NUMPAD_5); advance(21); capture("web-ultimate.png");
                    require(fight().snapshot().ultimates().stream().anyMatch(u->u.kind()==FighterKind.FEIDUDU), "Web ritual missing");
                    click("fight-restart"); startBattle(); step++;
                }
                case 5 -> {
                    down(Input.Keys.DOWN); key(Input.Keys.U); advance(85); up(Input.Keys.DOWN);
                    require(fight().snapshot().fighters().get(1).health() == 99, "Guard failed"); capture("guard.png");
                    click("fight-select"); require(fight().snapshot().state() == SessionState.PAUSED, "Selector did not pause");
                    key(Input.Keys.ESCAPE); require(fight().snapshot().state() == SessionState.PAUSED, "Selector close resumed fight");
                    ui().stage().act(.2f); ui().stage().act(.2f);
                    require(!fight().selection().active(), "Selection cancel left overlay active");
                    click("fight-restart"); startBattle(); down(Input.Keys.D); down(Input.Keys.LEFT);
                    for(int i=0;i<160&&Math.abs(fight().snapshot().fighters().get(1).x()-fight().snapshot().fighters().get(0).x())>110;i++){advance(1);}
                    up(Input.Keys.LEFT); up(Input.Keys.D);advance(4);
                    require(Math.abs(fight().snapshot().fighters().get(1).x() - fight().snapshot().fighters().get(0).x()) < 155,
                            "Punch capture did not approach opponent: " + fight().snapshot());
                    key(Input.Keys.J); advance(3); capture("punch.png");
                    for(int i=0;i<8&&fight().snapshot().hitstopTicks()==0;i++){advance(1);}
                    require(fight().particleCount()>=27,"Layered hit effects missing"); advance(4); capture("v2-impact.png"); advance(14);
                    boolean combo3=false;
                    for(int i=0;i<70;i++){if(i%23==0){key(Input.Keys.J);}advance(1);if(fight().snapshot().fighters().get(0).action()==FightSnapshot.Action.PUNCH_3){combo3=true;capture("v2-combo3.png");break;}}
                    require(combo3,"Three-stage punch never appeared");
                    for (int i = 0; i < 100 && fight().snapshot().state() == SessionState.RUNNING; i++) {
                        down(Input.Keys.D); advance(12); up(Input.Keys.D); key(Input.Keys.J); advance(23);
                    }
                    require(fight().snapshot().result() == FightSnapshot.Result.PLAYER_ONE, "Punch match never settled");
                    capture("knockout.png"); capture("v2-ko.png"); capture("v2-result-panel.png"); var end = fight().snapshot(); advance(30); require(end.equals(fight().snapshot()), "Terminal state advanced");
                    verifyVictory(0,"naiwa");verifySecondWinner();
                    click("fight-restart"); require(fight().snapshot().tick() == 0 && fight().snapshot().fighters().get(0).health() == 100, "Restart stale");
                    startBattle(); key(Input.Keys.K);advance(20);key(Input.Keys.K);advance(2);
                    require(fight().snapshot().fighters().get(0).jumpCount()==2,"Second jump not available");capture("v2-double-jump.png");
                    advance(85);require(fight().snapshot().fighters().get(0).platform()==0,"Low platform not landable");capture("v2-platform-stand.png");
                    down(Input.Keys.S);key(Input.Keys.K);up(Input.Keys.S);advance(2);
                    require(!fight().snapshot().fighters().get(0).grounded() && fight().snapshot().fighters().get(0).vy()<0,"Drop-through blocked");capture("v2-drop-through.png");advance(60);
                    down(Input.Keys.D);advance(30);require(fight().snapshot().fighters().get(0).action()==FightSnapshot.Action.RUN,"Holding direction did not run: "+fight().snapshot().fighters().get(0));
                    capture("v2-run.png");capture("v2-sprint-dust.png");up(Input.Keys.D);advance(4);key(Input.Keys.F1);capture("v2-hitboxes.png");key(Input.Keys.F1);
                    previousDisplay = assets().fightDisplay(); Gdx.graphics.setWindowedMode(1600, 900); step++;
                }
                case 6 -> { advance(18); require(previousDisplay != assets().fightDisplay() && previousDisplay.getRegions().first().getTexture().getTextureObjectHandle() == 0,"Display font did not refresh/release at larger DPI"); recordDisplayMetrics();verifyIconHud();capture("icon-hud-1600.png"); capture("duel-1600.png");capture("v2-1600.png"); previousDisplay=assets().fightDisplay(); Gdx.graphics.setWindowedMode(960, 640); step++; }
                case 7 -> { advance(18); require(previousDisplay != assets().fightDisplay() && previousDisplay.getRegions().first().getTexture().getTextureObjectHandle() == 0,"Display font did not refresh/release after shrinking"); recordDisplayMetrics();
                    var keys=actor("fight-keys-2");var point=keys.localToStageCoordinates(new Vector2());require(point.y>=2&&point.y+keys.getHeight()<=ui().stage().getViewport().getWorldHeight(),"Small-window key instructions clipped");
                    verifyIconHud();capture("icon-hud-small.png");capture("duel-small.png");verifySmallSelection(); Gdx.graphics.setWindowedMode(1280, 720); click("return-home"); step++; }
                case 8 -> { ((TextField) actor("search")).setText("肥嘟嘟"); click("game-fight"); step++; }
                case 9 -> { click("mode-bot"); click("start-preview"); step++; }
                case 10 -> {
                    capture("select-bot.png");click("select-next");click("select-next"); advance(90); require(fight().snapshot().tick()==0 && fight().snapshot().fighters().get(0).health()==100,"Bot attacked during opening"); capture("intro-bot.png"); key(Input.Keys.ENTER);
                    advance(900); require(fight().snapshot().fighters().get(0).health() < 100, "Bot inactive");
                    capture("bot-fighting.png"); click("return-home"); step++;
                }
                case 11 -> { gl = Gdx.gl.glGetString(GL20.GL_VERSION); lastDisplay=assets().fightDisplay(); finished = true; click("exit"); step++; }
                default -> { }
            }
            expectRunningBetweenSteps = getScreen() instanceof FightScreen && fight().snapshot().state() == SessionState.RUNNING;
            nativePauseBetweenSteps = false;
            require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "Fight GL error");
        }
        /** 真实娱乐键、八姿态和聚焦：比赛仍推进，受击/操作可打断，暂停/旧键清理与高台镜头正确。 */
        private void verifyLaugh(){
            require(fight().laughLoaded(),"Laugh atlas missing");laughTexture=assets().fighterSheet("fighters/fighters-laugh-v1.png");
            for(char c:"奶蛙捧腹大笑".toCharArray()){require(assets().fightDisplay().getData().hasGlyph(c),"Laugh title glyph missing");}
            down(Input.Keys.Y);advance(1);require(fight().snapshot().fighters().get(0).action()==FightSnapshot.Action.LAUGH&&fight().laughFocus()==0,"Y did not make Naiwa laugh/focus");
            boolean[] seen=new boolean[8];long start=fight().snapshot().tick();
            for(int i=0;i<195;i++){int pose=dev.gamecore.client.fight.FightLaughMotion.pose(fight().snapshot().fighters().get(0).poseTick());
                if(fight().snapshot().fighters().get(0).action()==FightSnapshot.Action.LAUGH&&!seen[pose]){seen[pose]=true;capture("laugh-pose-"+pose+".png");}
                if(i==80){down(Input.Keys.Y);}advance(1);
            }up(Input.Keys.Y);for(boolean pose:seen){require(pose,"Laugh choreography skipped a pose");}
            require(fight().snapshot().tick()>start+150&&fight().snapshot().fighters().get(0).health()==100&&fight().snapshot().fighters().get(0).attacks()==0,"Laugh froze world or changed combat stats");
            require(fight().snapshot().fighters().get(0).action()!=FightSnapshot.Action.LAUGH&&fight().laughFocus()==-1,"Held key repeated laugh forever");advance(70);capture("laugh-return.png");
            for(int key:new int[]{Input.Keys.NUM_9,Input.Keys.NUMPAD_9}){key(key);advance(1);require(fight().laughFocus()==0,"Numeric 9 alias failed for only Naiwa");down(Input.Keys.D);advance(1);up(Input.Keys.D);require(fight().laughFocus()==-1,"Move did not cancel laugh");advance(5);}
            key(Input.Keys.Y);advance(35);pause();var frozen=fight().snapshot();fight().render(20);key(Input.Keys.Y);require(frozen.equals(fight().snapshot()),"Paused laugh advanced or accepted input");capture("laugh-paused.png");
            key(Input.Keys.P);advance(1);require(fight().snapshot().fighters().get(0).poseTick()==frozen.fighters().get(0).poseTick()+1,"Laugh did not resume from same pose");key(Input.Keys.J);advance(1);require(fight().laughFocus()==-1,"Attack did not cancel laugh");capture("laugh-cancel.png");advance(50);
            click("fight-restart");startBattle();key(Input.Keys.Y);key(Input.Keys.NUMPAD_4);
            for(int i=0;i<120&&fight().snapshot().fighters().get(0).health()==100;i++){advance(1);}
            require(fight().snapshot().fighters().get(0).health()==94&&fight().laughFocus()==-1,"Laugh blocked damage or ignored hit interrupt");capture("laugh-interrupted.png");
            configure(FighterKind.FEIDUDU,FighterKind.NAIWA,dev.gamecore.fight.FightMap.TEMPLE);startBattle();key(Input.Keys.Y);advance(45);require(fight().laughFocus()==1,"Swapped Naiwa did not receive focus");capture("laugh-p2.png");
            configure(FighterKind.NAIWA,FighterKind.NAIWA,dev.gamecore.fight.FightMap.NEON);startBattle();key(Input.Keys.Y);advance(30);key(Input.Keys.NUMPAD_9);advance(1);
            require(fight().snapshot().fighters().stream().allMatch(f->f.action()==FightSnapshot.Action.LAUGH)&&fight().laughFocus()==1,"Two Naiwas did not have independent laughter");capture("laugh-two-naiwas.png");
            configure(FighterKind.NAIWA,FighterKind.FEIDUDU,dev.gamecore.fight.FightMap.NEON);startBattle();key(Input.Keys.K);advance(80);key(Input.Keys.Y);advance(45);require(fight().laughFocus()==0&&fight().snapshot().fighters().get(0).y()==120,"Platform laugh changed support");capture("laugh-platform.png");key(Input.Keys.K);advance(1);require(fight().laughFocus()==-1,"Jump did not cancel laugh");
            configure(FighterKind.FEIDUDU,FighterKind.FEIDUDU,dev.gamecore.fight.FightMap.NEON);startBattle();key(Input.Keys.Y);key(Input.Keys.NUM_9);advance(2);require(fight().laughFocus()==-1,"Feidudu received Naiwa-only emote");
            configure(FighterKind.NAIWA,FighterKind.FEIDUDU,dev.gamecore.fight.FightMap.NEON);startBattle();
        }
        /** 完整走两页选择、草稿/键盘/确认键释放边界，规则全程保持 READY。 */
        private void verifySelection(){
            for(char glyph:"选择你的斗士可返回平台布局或近战".toCharArray()){require(assets().fightDisplay().getData().hasGlyph(glyph),"Selection display glyph missing: "+glyph);}
            require(fight().selection().active(),"Initial selection screen missing");var ready=fight().snapshot();advance(45);require(ready.equals(fight().snapshot()),"Preview advanced READY rules");capture("select-fighters.png");
            click("select-fighter-0-feidudu");click("select-fighter-1-naiwa");
            require(fight().selection().fighter(0)==FighterKind.FEIDUDU&&fight().selection().fighter(1)==FighterKind.NAIWA,"Character cards ignored");require(ready.equals(fight().snapshot()),"Character draft changed live world");capture("select-fighters-swapped.png");
            key(Input.Keys.A);key(Input.Keys.RIGHT);require(fight().selection().fighter(0)==FighterKind.NAIWA&&fight().selection().fighter(1)==FighterKind.FEIDUDU,"Keyboard choices ignored");
            down(Input.Keys.ENTER);down(Input.Keys.ENTER);require(fight().selection().page()==dev.gamecore.client.fight.FightSelection.Page.MAPS&&!fight().opening().active(),"Repeated Enter skipped map selection");up(Input.Keys.ENTER);
            for(var map:dev.gamecore.fight.FightMap.values()){click("select-map-"+map.name().toLowerCase(java.util.Locale.ROOT));
                for(char c:(map.toString()+map.feature()).toCharArray()){if(!Character.isWhitespace(c)){require(assets().fightDisplay().getData().hasGlyph(c),"Map display glyph missing: "+c);}}
                capture("select-map-"+map.name().toLowerCase(java.util.Locale.ROOT)+".png");require(ready.equals(fight().snapshot()),"Map preview started combat");}
            key(Input.Keys.E);require(fight().selection().map()==dev.gamecore.fight.FightMap.NEON,"Map keyboard wrap failed");key(Input.Keys.ESCAPE);require(fight().selection().page()==dev.gamecore.client.fight.FightSelection.Page.FIGHTERS,"Map back skipped fighter page");
            click("select-fighter-1-naiwa");capture("select-same-fighters.png");click("select-fighter-1-feidudu");click("select-next");
            down(Input.Keys.D);down(Input.Keys.ENTER);down(Input.Keys.ENTER);require(fight().opening().active()&&fight().snapshot().tick()==0,"Confirm did not start opening or repeat skipped it");up(Input.Keys.ENTER);up(Input.Keys.D);
            key(Input.Keys.ENTER);advance(2);require(fight().snapshot().fighters().get(0).x()==560&&fight().snapshot().fighters().get(0).attacks()==0,"Selection leaked held input into combat");click("fight-restart");
        }
        /** 通过正式卡片确认配置；兼容旧战斗场景以 READY 开始，绝不直接设置规则/草稿。 */
        private void configure(FighterKind one,FighterKind two,dev.gamecore.fight.FightMap map){
            if(!fight().selection().active()){click("fight-select");}click("select-step-fighters");
            click("select-fighter-0-"+one.name().toLowerCase(java.util.Locale.ROOT));click("select-fighter-1-"+two.name().toLowerCase(java.util.Locale.ROOT));click("select-next");
            click("select-map-"+map.name().toLowerCase(java.util.Locale.ROOT));click("select-next");
            require(fight().snapshot().map()==map&&fight().snapshot().fighters().get(0).kind()==one&&fight().snapshot().fighters().get(1).kind()==two,"Confirmed selection was not applied");
            require(fight().snapshot().tick()==0&&fight().particleCount()==0&&fight().opening().active()&&!fight().victory().finished(),"Confirmed selection retained old round state");click("fight-restart");
        }
        private void verifySmallSelection(){
            down(Input.Keys.D);click("fight-select");var paused=fight().snapshot();capture("select-fighters-small.png");checkSelectionBounds();
            for(int i=0;i<18;i++){key(Input.Keys.TAB);var focused=ui().stage().getKeyboardFocus();require(focused!=null&&focused.isDescendantOf(actor("fight-selection")),"Tab entered hidden combat controls");}
            click("select-next");capture("select-maps-small.png");checkSelectionBounds();
            for(var map:dev.gamecore.fight.FightMap.values()){click("select-map-"+map.name().toLowerCase(java.util.Locale.ROOT));require(fight().selection().map()==map,"Small-window map card unreachable");}capture("select-frost-small.png");click("select-map-temple");
            pause();double age=fight().selectionPanel().previewTime();advance(30);require(age==fight().selectionPanel().previewTime(),"Focus loss did not freeze selection preview");
            key(Input.Keys.ESCAPE);key(Input.Keys.ESCAPE);up(Input.Keys.D);require(!fight().selection().active()&&paused.equals(fight().snapshot()),"Cancel lost paused original match");key(Input.Keys.P);advance(2);
            require(fight().snapshot().fighters().get(0).x()==paused.fighters().get(0).x(),"Selection retained movement input");
        }
        private void checkSelectionBounds(){for(var control:fight().selectionPanel().getChildren()){if(!control.isVisible()){continue;}var pos=control.localToStageCoordinates(new Vector2());
            require(pos.x>=0&&pos.y>=0&&pos.x+control.getWidth()<=ui().stage().getViewport().getWorldWidth()+.1f&&pos.y+control.getHeight()<=ui().stage().getViewport().getWorldHeight()+.1f,"Selection control clipped: "+control.getName());}}
        /** 双方在原出生点打空：验姿态轮换/命中步/收拳，并用真实短按检查只缓存一拳。 */
        private void startBattle() {
            click("fight-action");
            if (fight().opening().active()) { key(Input.Keys.ENTER); }
            require(fight().snapshot().state() == SessionState.RUNNING, "Battle did not begin after explicit skip");
        }
        /** 实际选图/跳跃验证平台高度，不只换背景；切图必须丢弃旧对局和持续输入。 */
        private void verifyMaps(){
            require(fight().sceneryMaterialsLoaded(),"Scenery materials failed to load");materialTexture=assets().fightSceneryMaterials();require(materialTexture==assets().fightSceneryMaterials(),"Material atlas loaded twice");
            for(var map:dev.gamecore.fight.FightMap.values()){
                configure(FighterKind.NAIWA,FighterKind.FEIDUDU,map);require(fight().snapshot().map()==map&&fight().snapshot().tick()==0,"Map choice not applied");
                verifyMapHud(map);
                capture("map-"+map.name().toLowerCase(java.util.Locale.ROOT)+"-ready.png");
                var texture=assets().fightStage(map);require(texture!=null,"Selected map art missing");stageTextures.add(texture);require(assets().fightStage(map)==texture,"Map art was reloaded unnecessarily");
                startBattle();key(Input.Keys.K);advance(80);
                require(fight().snapshot().fighters().get(0).y()==map.platforms().get(0).height(),"Map-specific platform height not used");
                capture("map-"+map.name().toLowerCase(java.util.Locale.ROOT)+"-platform.png");
                verifySceneryRoute(map);
                if(map==dev.gamecore.fight.FightMap.TEMPLE){down(Input.Keys.D);click("fight-select");var paused=fight().snapshot();require(paused.state()==SessionState.PAUSED,"Selection did not pause");click("select-step-maps");click("select-map-forge");advance(30);require(paused.equals(fight().snapshot()),"Draft changed paused world");key(Input.Keys.ESCAPE);key(Input.Keys.ESCAPE);up(Input.Keys.D);require(paused.equals(fight().snapshot()),"Cancel changed or resumed original fight");}
            }
            configure(FighterKind.NAIWA,FighterKind.FEIDUDU,dev.gamecore.fight.FightMap.NEON);
        }
        /** 各图通过正式两跳攀到最高建筑，再下跳到更低落脚点；F1 截图显示真实顶沿。 */
        private void verifySceneryRoute(dev.gamecore.fight.FightMap map){
            int[] route=map==dev.gamecore.fight.FightMap.FORGE?new int[]{2,3}:map==dev.gamecore.fight.FightMap.BAMBOO||map==dev.gamecore.fight.FightMap.FROST?new int[]{3,5}:new int[]{3};
            for(int id:route){var p=map.platforms().get(id);jumpToScenery(map==dev.gamecore.fight.FightMap.FORGE&&id==2?880:(p.left()+p.right())/2,p.height());}
            double highest=map.platforms().stream().mapToDouble(dev.gamecore.fight.FightArena.Plate::height).max().orElseThrow();require(fight().snapshot().fighters().get(0).y()==highest,"Scenery highest route unreachable");
            for(int i=0;i<8&&fight().snapshot().fighters().get(0).action()==FightSnapshot.Action.LAND;i++){advance(1);}
            require(fight().snapshot().fighters().get(0).action()==FightSnapshot.Action.IDLE,"Route did not finish landing recovery");
            capture("scene-"+map.name().toLowerCase(java.util.Locale.ROOT)+"-highest.png");key(Input.Keys.F1);capture("scene-"+map.name().toLowerCase(java.util.Locale.ROOT)+"-supports.png");key(Input.Keys.F1);
            down(Input.Keys.S);key(Input.Keys.K);up(Input.Keys.S);advance(1);require(!fight().snapshot().fighters().get(0).grounded(),"Decorative building blocked drop-through");advance(80);
        }
        private void jumpToScenery(double x,double y){
            int held=0;
            for(int t=0;t<130;t++){var f=fight().snapshot().fighters().get(0);double stop=f.x()+f.vx()*Math.abs(f.vx())/.68;int move=stop<x-12?1:stop>x+12?-1:Math.abs(f.vx())>.6?-(int)Math.signum(f.vx()):0;
                if(move!=held){if(held!=0){up(held>0?Input.Keys.D:Input.Keys.A);}if(move!=0){down(move>0?Input.Keys.D:Input.Keys.A);}held=move;}
                if(t==0||t==19&&!f.grounded()){key(Input.Keys.K);}advance(1);
                if(t>8&&fight().snapshot().fighters().get(0).grounded()){if(held!=0){up(held>0?Input.Keys.D:Input.Keys.A);}advance(4);require(fight().snapshot().fighters().get(0).y()==y,"Scenery route landed on wrong roof/column: "+fight().snapshot());return;}
            }throw new IllegalStateException("Scenery route never landed");
        }
        /** 真实 KO 后取景/八姿态/失焦冻结；对局快照必须始终相等，不能把庆祝当战斗模拟。 */
        private void verifyVictory(int winner,String role){
            require(fight().victoryLoaded()&&fight().victory().winner()==winner,"Wrong winner or victory art missing");
            var end=fight().snapshot();advance(45);require(fight().victory().focusedPlayer()==winner,"Winner never received close-up");capture("victory-"+role+"-focus.png");
            pause();double age=fight().victory().elapsed();advance(60);require(fight().victory().paused()&&age==fight().victory().elapsed(),"Victory did not pause on focus loss");key(Input.Keys.P);
            boolean[] seen=new boolean[8];
            for(int i=0;i<330;i++){advance(1);int pose=fight().victory().pose();if(pose>=0&&!seen[pose]){seen[pose]=true;capture("victory-"+role+"-pose-"+pose+".png");}require(end.equals(fight().snapshot()),"Celebration advanced finished rules");}
            for(boolean pose:seen){require(pose,"Kung fu choreography skipped a pose");}require(fight().victory().pose()==7,"Victory routine did not hold final pose");
        }
        private void verifySecondWinner(){
            click("fight-restart");startBattle();down(Input.Keys.D);down(Input.Keys.LEFT);
            for(int i=0;i<160&&Math.abs(fight().snapshot().fighters().get(1).x()-fight().snapshot().fighters().get(0).x())>110;i++){advance(1);}up(Input.Keys.D);up(Input.Keys.LEFT);advance(4);
            for(int i=0;i<100&&fight().snapshot().state()==SessionState.RUNNING;i++){down(Input.Keys.LEFT);advance(12);up(Input.Keys.LEFT);key(Input.Keys.NUMPAD_1);advance(23);}
            require(fight().snapshot().result()==FightSnapshot.Result.PLAYER_TWO,"P2 knockout failed");verifyVictory(1,"feidudu");
        }
        /** 真正播放完整演出，其他旧战斗场景显式用正式 Enter 跳过，不伪造会话状态。 */
        private void verifyOpening() {
            click("fight-action"); require(fight().greetingsLoaded(), "Greeting atlas failed to load");
            var ready = fight().snapshot();
            down(Input.Keys.D); key(Input.Keys.J); key(Input.Keys.I);key(Input.Keys.Y);key(Input.Keys.NUM_9); advance(48); up(Input.Keys.D);
            require(ready.equals(fight().snapshot()), "Opening advanced combat or consumed skill energy"); capture("intro-naiwa.png");
            pause(); double frozen = fight().opening().elapsed(); fight().render(.1f);
            require(fight().opening().paused() && frozen == fight().opening().elapsed(), "Focus loss did not freeze opening"); key(Input.Keys.ENTER); require(fight().opening().paused(), "Paused opening was skipped"); capture("intro-paused.png");
            key(Input.Keys.P); advance(90); require(fight().opening().focusedPlayer() == 1, "Second character never received focus"); capture("intro-feidudu.png");
            for (int number = 3; number >= 1; number--) {
                for (int i = 0; i < 120 && fight().opening().number() != number; i++) { advance(1); }
                require(fight().opening().number() == number, "Countdown skipped " + number); advance(7); capture("intro-count-" + number + ".png");
                require(ready.equals(fight().snapshot()), "Countdown changed READY rules");
            }
            for (int i = 0; i < 90 && fight().opening().phase() != dev.gamecore.client.fight.FightOpening.Phase.FIGHT; i++) { advance(1); }
            capture("intro-fight.png");
            for (int i=0;i<90&&fight().opening().active();i++) { advance(1); }
            require(fight().snapshot().state() == SessionState.RUNNING && fight().snapshot().tick() == 0, "Opening must begin rules exactly at Tick zero");
            advance(2); require(fight().snapshot().fighters().get(0).attacks() == 0, "Opening leaked buffered attack");
            click("fight-restart");
            configure(FighterKind.FEIDUDU,FighterKind.NAIWA,dev.gamecore.fight.FightMap.NEON);
            click("fight-action"); advance(48); require(fight().opening().focusedPlayer()==1,"Swapped Naiwa was not introduced first"); capture("intro-swapped-naiwa.png"); key(Input.Keys.ENTER);
            require(fight().snapshot().tick()==0&&fight().snapshot().state()==SessionState.RUNNING,"Explicit skip advanced rules");
            configure(FighterKind.NAIWA,FighterKind.FEIDUDU,dev.gamecore.fight.FightMap.NEON);
            recordDisplayMetrics();
        }
        private void recordDisplayMetrics() {
            var font=assets().fightDisplay(); long bytes=0;
            for(var region:font.getRegions()){var texture=region.getTexture();bytes+=(long)texture.getWidth()*texture.getHeight()*4;}
            displayMetrics.append("DisplayDensity=").append(assets().fontDensity()).append(", Pages=").append(font.getRegions().size).append(", RgbaBytes=").append(bytes).append('\n');
        }
        private void verifyBoxing() {
            require(fight().boxingLoaded(), "Dedicated boxing atlas failed to load");
            advance(45);
            int number = 0;
            for (var punch : new FightPunch[]{FightPunch.JAB, FightPunch.CROSS, FightPunch.HOOK, FightPunch.JAB}) {
                key(Input.Keys.J); key(Input.Keys.NUMPAD_1); advance(1);
                for (var f : fight().snapshot().fighters()) { require(f.action() == punch.action(), "Whiffed punches did not alternate: " + f); }
                if (number < 3) { capture("boxing-" + number + "-windup.png"); }
                advance(punch.strikeTick());
                for (var f : fight().snapshot().fighters()) { require(f.poseTick() == punch.strikeTick() && f.attacks() == number + 1, "Boxing impact timing drifted"); }
                if (number < 3) { capture("boxing-" + number + "-impact.png"); }
                advance(4);
                if (number < 3) { capture("boxing-" + number + "-recovery.png"); }
                advance(punch.duration() - punch.strikeTick() - 2); number++;
            }
            for (var f : fight().snapshot().fighters()) { require(f.health() == 100 && f.hits() == 0, "Air punches caused damage"); }
            click("fight-restart"); startBattle(); key(Input.Keys.J); advance(1);
            for (int i = 0; i < 6; i++) { key(Input.Keys.J); advance(1); }
            advance(55); require(fight().snapshot().fighters().get(0).attacks() == 2, "Fast clicks lost next punch or queued endless punches");
            click("fight-restart"); startBattle();
        }
        /** 只通过真实按键得到命中，检查对应视觉、冻结/清理和图标状态；伤害仍由原规则给出。 */
        private void verifyImpactUi(){
            verifyIconHud();capture("icon-hud-ready.png");
            require(slot(0,"ranged").ready()&&!slot(0,"ultimate").ready(),"Skill energy state wrong at 30 EN");
            require(slot(0,"ranged").icon()==dev.gamecore.client.fight.FightIcon.BOLT&&slot(1,"ranged").icon()==dev.gamecore.client.fight.FightIcon.WEB,"Element icons do not follow characters");
            approach();int index=0;
            for(var style:new dev.gamecore.client.fight.FightImpactFeedback.Style[]{dev.gamecore.client.fight.FightImpactFeedback.Style.JAB,dev.gamecore.client.fight.FightImpactFeedback.Style.CROSS,dev.gamecore.client.fight.FightImpactFeedback.Style.HOOK}){
                int before=fight().snapshot().fighters().get(0).hits();key(Input.Keys.J);
                for(int i=0;i<22&&fight().snapshot().fighters().get(0).hits()==before;i++){advance(1);}
                var effect=fight().impactFeedback().hit(index++);require(effect.alive()&&effect.style()==style&&effect.victim()==1,"Wrong real punch feedback: "+effect.style());
                capture("impact-"+style.name().toLowerCase(java.util.Locale.ROOT)+".png");
                if(index==1){pause();float age=effect.age();var frozen=fight().snapshot();fight().render(20);require(effect.age()==age&&frozen.equals(fight().snapshot()),"Paused impact advanced");capture("impact-paused.png");key(Input.Keys.P);advance(1);}
                for(int i=0;i<45&&fight().snapshot().fighters().get(0).action()!=FightSnapshot.Action.IDLE;i++){advance(1);}
            }
            freshImpactRound();approach();key(Input.Keys.NUMPAD_1);awaitImpact();require(fight().impactFeedback().hit(0).direction()==-1&&fight().impactFeedback().hit(0).victim()==0,"P2 feedback mirror/victim wrong");capture("impact-p2.png");
            for(int player=0;player<2;player++){
                freshImpactRound();key(player==0?Input.Keys.U:Input.Keys.NUMPAD_4);awaitImpact();
                var expected=player==0?dev.gamecore.client.fight.FightImpactFeedback.Style.LIGHTNING:dev.gamecore.client.fight.FightImpactFeedback.Style.WEB;
                require(fight().impactFeedback().hit(0).style()==expected,"Ranged impact type wrong");capture(player==0?"impact-lightning.png":"impact-web.png");
            }
            freshImpactRound();down(Input.Keys.DOWN);key(Input.Keys.U);awaitImpact();up(Input.Keys.DOWN);
            require(fight().impactFeedback().hit(0).style()==dev.gamecore.client.fight.FightImpactFeedback.Style.BLOCK&&fight().snapshot().fighters().get(1).health()==99,"Shield visual did not correspond to actual reduced damage");capture("impact-block.png");
            for(int player=0;player<2;player++){
                freshImpactRound();advance(710);require(slot(player,"ultimate").ready(),"Full energy ultimate icon still dim");key(player==0?Input.Keys.I:Input.Keys.NUMPAD_5);advance(1);require(!slot(player,"ultimate").ready(),"Spent energy ultimate still shown ready");awaitImpact();
                require(fight().impactFeedback().hit(0).style()==dev.gamecore.client.fight.FightImpactFeedback.Style.ULTIMATE,"Ultimate has normal hit feedback");capture(player==0?"impact-lightning-ultimate.png":"impact-web-ultimate.png");
            }
            freshImpactRound();
        }
        private void freshImpactRound(){click("fight-restart");require(fight().impactFeedback().count()==0,"Restart kept old impact effects");startBattle();}
        /** 完整大招与闪袭走实际输入，截图来自真实规则阶段，而不是先播一段不结算的动画。 */
        private void verifySpecials(){
            for(char c:"九天雷引八方蛛狱蛛茧膨胀时空闪袭".toCharArray()){require(assets().fightDisplay().getData().hasGlyph(c),"Special display glyph missing: "+c);}
            freshSpecialRound();advance(710);key(Input.Keys.I);advance(20);require(fight().specialFocus().player()==0&&fight().cameraZoom()>1.9f,"Naiwa charge did not focus");capture("special-lightning-charge.png");
            pause();var frozen=fight().snapshot();float zoom=fight().cameraZoom();fight().render(20);require(frozen.equals(fight().snapshot())&&zoom==fight().cameraZoom(),"Special pause changed ritual or camera");capture("special-lightning-paused.png");key(Input.Keys.P);
            ritualAge(0,40);capture("special-lightning-release.png");
            for(int i=0;i<dev.gamecore.fight.FightSpecial.BOLT_COUNT;i++){ritualAge(0,dev.gamecore.fight.FightSpecial.boltTick(i));require(fight().cameraZoom()<1.1f,"Caster camera hid first strike");capture("special-lightning-bolt-"+i+".png");}
            advance(65);require(fight().snapshot().fighters().get(1).health()==80&&fight().snapshot().ultimates().isEmpty(),"Thunder ritual damage/cleanup wrong");
            freshSpecialRound();advance(710);key(Input.Keys.NUMPAD_5);advance(20);require(fight().specialFocus().player()==1,"Feidudu charge did not focus");capture("special-web-charge.png");
            ritualAge(1,51);capture("special-web-converge.png");ritualAge(1,dev.gamecore.fight.FightSpecial.WEB_CAPTURE);
            require(fight().snapshot().fighters().get(0).boundBy()>0&&fight().snapshot().fighters().get(0).health()==100,"Web damaged early or did not really bind");capture("special-web-cocoon-small.png");
            double boundX=fight().snapshot().fighters().get(0).x();down(Input.Keys.D);key(Input.Keys.K);advance(12);up(Input.Keys.D);require(fight().snapshot().fighters().get(0).x()==boundX,"Bound target moved through cocoon");
            ritualAge(1,101);capture("special-web-cocoon-large.png");pause();frozen=fight().snapshot();fight().render(10);require(frozen.equals(fight().snapshot()),"Paused cocoon inflated or damaged");capture("special-web-paused.png");key(Input.Keys.P);
            ritualAge(1,dev.gamecore.fight.FightSpecial.WEB_BURST);require(fight().snapshot().fighters().get(0).health()==80&&fight().snapshot().fighters().get(0).boundBy()==0,"Web burst failed to release and damage");capture("special-web-burst.png");ritualAge(1,119);capture("special-web-burst-peak.png");advance(85);capture("special-web-after.png");
            freshSpecialRound();advance(710);key(Input.Keys.NUMPAD_5);advance(1);ritualAge(1,dev.gamecore.fight.FightSpecial.WEB_AIM);down(Input.Keys.A);ritualAge(1,70);up(Input.Keys.A);require(ritual(1).missed()&&fight().snapshot().fighters().get(0).health()==100&&fight().snapshot().fighters().get(0).boundBy()==0,"Dodged web still bound/damaged");capture("special-web-evaded.png");
            freshSpecialRound();down(Input.Keys.DOWN);advance(1);key(Input.Keys.NUMPAD_2);advance(1);up(Input.Keys.DOWN);
            require(fight().snapshot().teleports().size()==1&&fight().snapshot().fighters().get(0).health()==95&&fight().snapshot().fighters().get(0).vx()>0,"Blink did not instantly knock target back");capture("special-blink-arrival.png");advance(6);require(fight().cameraZoom()<1.21f,"Blink over-zoomed");capture("special-blink-focus.png");
            advance(35);down(Input.Keys.DOWN);advance(1);key(Input.Keys.NUMPAD_2);advance(1);up(Input.Keys.DOWN);require(fight().snapshot().fighters().get(1).teleportCooldown()>0&&fight().snapshot().fighters().get(0).health()==95,"Cooldown repeated blink");capture("special-blink-cooldown.png");
            freshSpecialRound();key(Input.Keys.K);advance(70);require(fight().snapshot().fighters().get(0).platform()==0,"Roof target did not land");down(Input.Keys.DOWN);advance(1);key(Input.Keys.NUMPAD_2);advance(1);up(Input.Keys.DOWN);
            require(fight().snapshot().fighters().get(1).platform()==0&&fight().snapshot().fighters().get(1).y()==120,"Blink destination lost real roof support");key(Input.Keys.F1);capture("special-blink-roof.png");key(Input.Keys.F1);
            advance(40);down(Input.Keys.DOWN);advance(1);down(Input.Keys.SHIFT_LEFT);key(Input.Keys.NUMPAD_2);advance(1);up(Input.Keys.SHIFT_LEFT);up(Input.Keys.DOWN);
            require(fight().snapshot().fighters().get(1).y()<120&&fight().snapshot().fighters().get(1).vy()<0,"Shift explicit drop from guard failed");capture("special-shift-drop.png");
            configure(FighterKind.FEIDUDU,FighterKind.NAIWA,dev.gamecore.fight.FightMap.FROST);startBattle();advance(710);key(Input.Keys.NUMPAD_5);advance(20);require(fight().specialFocus().player()==1,"Swapped Naiwa focus wrong");capture("special-lightning-p2-focus.png");ritualAge(1,54);capture("special-lightning-p2-bolt.png");
            freshSpecialRound();down(Input.Keys.S);advance(1);key(Input.Keys.K);advance(1);up(Input.Keys.S);require(fight().snapshot().teleports().getFirst().owner()==0,"P1 Feidudu S+K did not blink");capture("special-blink-p1.png");
            configure(FighterKind.FEIDUDU,FighterKind.NAIWA,dev.gamecore.fight.FightMap.BAMBOO);startBattle();advance(710);key(Input.Keys.I);advance(1);ritualAge(0,100);require(fight().snapshot().fighters().get(1).boundBy()>0,"P1 web did not capture");capture("special-web-p1-cocoon.png");
            configure(FighterKind.FEIDUDU,FighterKind.FEIDUDU,dev.gamecore.fight.FightMap.NEON);startBattle();advance(710);key(Input.Keys.I);key(Input.Keys.NUMPAD_5);advance(1);ritualAge(0,100);require(fight().snapshot().fighters().stream().allMatch(f->f.boundBy()>0),"Dual webs lost a real binding");capture("special-web-both.png");ritualAge(0,114);require(fight().snapshot().fighters().stream().allMatch(f->f.health()==80&&f.boundBy()==0),"Dual web burst failed");capture("special-web-both-burst.png");
            configure(FighterKind.NAIWA,FighterKind.FEIDUDU,dev.gamecore.fight.FightMap.NEON);startBattle();
        }
        private FightSnapshot.Ultimate ritual(int owner){return fight().snapshot().ultimates().stream().filter(u->u.owner()==owner).findFirst().orElseThrow(()->new IllegalStateException("Ritual vanished for "+owner));}
        private void ritualAge(int owner,int age){for(int i=0;i<400&&ritual(owner).age()<age;i++){advance(1);}require(ritual(owner).age()==age,"Ritual skipped target age");}
        private void freshSpecialRound(){freshImpactRound();require(fight().snapshot().ultimates().isEmpty()&&fight().snapshot().teleports().isEmpty()&&fight().snapshot().fighters().stream().allMatch(f->f.boundBy()==0&&f.teleportCooldown()==0),"Restart kept ritual/binding/cooldown");}
        private void approach(){down(Input.Keys.D);down(Input.Keys.LEFT);for(int i=0;i<160&&fight().snapshot().fighters().get(1).x()-fight().snapshot().fighters().get(0).x()>94;i++){advance(1);}up(Input.Keys.D);up(Input.Keys.LEFT);advance(5);}
        private void awaitImpact(){for(int i=0;i<160&&fight().impactFeedback().count()==0;i++){advance(1);}require(fight().impactFeedback().count()>0,"Real attack produced no impact feedback");}
        private dev.gamecore.client.fight.FightHud.SkillSlot slot(int player,String skill){return (dev.gamecore.client.fight.FightHud.SkillSlot)actor("fight-skill-"+player+"-"+skill);}
        private void verifyIconHud(){
            ui().stage().act(0);for(int player=0;player<2;player++){for(var skill:dev.gamecore.client.fight.FightHudModel.Skill.values()){
                var slot=slot(player,skill.name().toLowerCase(java.util.Locale.ROOT));require(slot!=null,"Missing skill icon slot");var xy=slot.localToStageCoordinates(new Vector2());
                require(xy.x>=0&&xy.y>=0&&xy.x+slot.getWidth()<=ui().stage().getViewport().getWorldWidth()+.1f&&slot.getWidth()>65,"Skill HUD clipped or too narrow");
                require(slot.label().startsWith(skill.key(player)+" "),"HUD displayed wrong player's key");
            }}
        }
        /** 确认后的主题跟随实际地图；装饰和文字必须给四个真实按钮留下可点击区域。 */
        private void verifyMapHud(dev.gamecore.fight.FightMap map){
            ui().stage().act(0);ui().render(0);verifyIconHud();
            for(int player=0;player<2;player++){
                var hud=(dev.gamecore.client.fight.FightHud)actor("fight-hud-"+player);
                require(hud.skin()==dev.gamecore.client.fight.FightUiSkin.of(map),"HUD skin differs from committed map");
                var p=hud.localToStageCoordinates(new Vector2());require(p.x>=0&&p.x+hud.getWidth()<=ui().stage().getWidth()+.1f,"Map HUD outside stage");
            }
            for(String id:new String[]{"fight-action","fight-select","fight-restart","return-home"}){
                var target=actor(id);var p=target.localToStageCoordinates(new Vector2(target.getWidth()/2,target.getHeight()/2));var hit=ui().stage().hit(p.x,p.y,true);
                require(hit==target||hit!=null&&hit.isDescendantOf(target),"Decorative HUD blocked "+id);
                require(p.x>=0&&p.x<=ui().stage().getWidth()&&p.y>=0,"Map action outside stage");
            }
        }
        private void advance(int ticks) { while (ticks >= 5) { fight().render(5f / 60); ticks -= 5; } for (int i = 0; i < ticks; i++) { fight().render(1f / 60); } }
        private void down(int key) { Gdx.input.getInputProcessor().keyDown(key); }
        private void up(int key) { Gdx.input.getInputProcessor().keyUp(key); }
        private void key(int key) { down(key); up(key); }
        private void click(String name) {
            Actor target = actor(name); require(target != null, "Missing control " + name);
            for (Actor parent = target.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof ScrollPane scroll) {
                    Vector2 location = target.localToAscendantCoordinates(scroll.getActor(), new Vector2());
                    scroll.validate();
                    scroll.setScrollY(dev.gamecore.client.ui.ScrollReveal.offset(scroll.getScrollY(), scroll.getMaxY(),
                            scroll.getScrollHeight(), location.y, target.getHeight())); scroll.updateVisualScroll(); ui().stage().act(.2f); ui().stage().draw(); break;
                }
            }
            Vector2 point = target.localToStageCoordinates(new Vector2(target.getWidth() / 2, target.getHeight() / 2));
            Actor hit = ui().stage().hit(point.x, point.y, true);
            require(hit == target || hit != null && hit.isDescendantOf(target), "Unreachable " + name + " hit=" + hit);
            ui().stage().stageToScreenCoordinates(point);
            require(point.x >= 0 && point.y >= 0 && point.x < Gdx.graphics.getWidth() && point.y < Gdx.graphics.getHeight(), "Outside screen " + name);
            Gdx.input.getInputProcessor().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
            Gdx.input.getInputProcessor().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        }
        private void capture(String file) {
            for (int i = 0; i < 4; i++) { ui().stage().act(.1f); ui().render(0); }
            Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            try { PixmapIO.writePNG(new FileHandle(report.resolve(file).toFile()), pixels, 6, true); } finally { pixels.dispose(); }
        }
    }
}
