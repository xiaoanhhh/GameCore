package dev.gamecore.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.assets.ClientAssetManager;
import dev.gamecore.client.ui.ArenaArt;
import dev.gamecore.client.navigation.LobbyNavigation.Page;
import dev.gamecore.client.screen.PlayScreen;
import dev.gamecore.client.screen.SnakeScreen;
import dev.gamecore.snake.SnakeGame;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.client.settings.ClientSettings;
import dev.gamecore.client.settings.SettingsStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 通过真实窗口和 Scene2D 输入管线验收大厅；隔离用户配置并覆盖保存失败回滚。 */
public final class DesktopSmokeTest {
    private DesktopSmokeTest() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) { throw new IllegalArgumentException("Expected report directory"); }
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        Files.deleteIfExists(directory.resolve("result.txt"));
        Files.deleteIfExists(directory.resolve("preferences.properties"));
        Scenario client = new Scenario(directory, false);
        run(client);
        Path blocked = directory.resolve("blocked-parent");
        Files.writeString(blocked, "This file intentionally prevents a settings directory");
        Scenario failure = new Scenario(directory, true);
        run(failure);
        Files.writeString(directory.resolve("result.txt"),
                "GAMECORE_LOBBY_SMOKE_OK\nCatalogSearch=true\nMouseAndKeyboard=true\n"
                        + "ModesAndPause=true\nSettingsPersisted=true\nCancelDiscardsDraft=true\n"
                        + "SaveFailureRollback=true\nResizeAndLetterbox=true\nReboundKeyApplied=true\n"
                        + "SamePageDraftRetained=true\nDropdownEscClosed=true\nDoubleDisposeSafe=true\n"
                        + "AllVisitedScreensDisposed=true\nInputDetachedOnExit=true\nBackgroundLoaded=true\nBackgroundFallback=true\n"
                        + "BackgroundCoversLetterbox=true\nModeArtworkLinked=true\nBackgroundTextureReleased=true\n"
                        + "OpenGL=" + client.glVersion + "\nCompleted=true\n");
        System.out.println("GAMECORE_LOBBY_SMOKE_OK: navigation, settings, input, resize, error rollback, disposal");
    }

    private static void run(Scenario client) {
        Lwjgl3ApplicationConfiguration config = Lwjgl3Launcher.configuration(DesktopSettings.defaults());
        config.disableAudio(true);
        new Lwjgl3Application(client, config);
        require(client.finished && client.isDisposed(), "Scenario did not finish and dispose");
        require(client.seen.stream().allMatch(UiScreen::isDisposed), "A visited Stage was retained");
        require(Gdx.input.getInputProcessor() == null, "Input processor retained after exit");
        require(client.backgroundTexture == null || client.backgroundTexture.getTextureObjectHandle() == 0, "Background texture leaked");
        client.dispose();
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    /** 每三帧推进一次，让 postRunnable 和窗口 resize 在下一步断言前完成。 */
    private static final class Scenario extends GameCoreClient {
        private final FileHandle reports;
        private final boolean failureCase;
        private final List<UiScreen> seen = new ArrayList<>();
        private UiScreen previous;
        private int frame;
        private int step;
        private boolean finished;
        private String glVersion;
        private com.badlogic.gdx.graphics.Texture backgroundTexture;

        private Scenario(Path directory, boolean failureCase) {
            super(directory.resolve(failureCase ? "blocked-parent/settings.properties" : "preferences.properties"));
            this.failureCase = failureCase;
            reports = new FileHandle(directory.toFile());
        }

        @Override protected ClientAssetManager createAssets() {
            var resources = failureCase ? new ClientAssetManager("backgrounds/intentionally-missing.png") : super.createAssets();
            backgroundTexture = resources.background;
            require((backgroundTexture == null) == failureCase, "Background load/fallback did not follow injected resource");
            return resources;
        }

        @Override public void render() {
            super.render();
            UiScreen current = (UiScreen) getScreen();
            if (current != previous) {
                if (previous != null) { require(previous.isDisposed(), "Old screen not disposed during transition"); }
                seen.add(current);
                previous = current;
            }
            if (++frame % 3 != 0) { return; }
            if (failureCase) { failureStep(); } else { normalStep(); }
            require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "OpenGL error during lobby scenario");
        }

        private void normalStep() {
            switch (step++) {
                case 0 -> {
                    page(Page.HOME);
                    require(ui().lobbyUi(),"Lobby did not use scoped lobby theme");
                    require(actor("game-snake") != null && actor("game-tank") != null, "Catalog cards missing");
                    String characters = Gdx.files.internal("fonts/characters.txt").readString("UTF-8");
                    for (char character : characters.toCharArray()) {
                        if (!Character.isWhitespace(character)) {
                            require(assets().body.getData().hasGlyph(character), "Missing font glyph: " + character);
                        }
                    }
                    snapshot("home.png");
                    ui().stage().setKeyboardFocus(actor("game-fight"));
                    snapshot("home-fight-focused.png");
                    require(hittable(actor("game-fight")), "Third catalog card not revealed by focus");
                    ui().stage().setKeyboardFocus(actor("game-snake"));
                    snapshot("home-focused.png");
                    click("game-snake");
                }
                case 1 -> { page(Page.DETAIL); snapshot("snake-detail.png"); click("mode-obstacles"); }
                case 2 -> {
                    require(((ArenaArt) actor("detail-art")).modeId().equals("obstacles"), "Mode artwork not synchronized");
                    snapshot("snake-detail-obstacles.png");
                    click("start-preview");
                }
                case 3 -> {
                    page(Page.PREVIEW);
                    require(!ui().lobbyUi(),"Lobby theme leaked into Snake arena");
                    require(navigation().current().modeId().equals("obstacles"), "Chosen mode lost");
                    SnakeScreen play = (SnakeScreen) getScreen();
                    key(Input.Keys.SPACE);
                    int y = play.snapshot().players().getFirst().head().y();
                    key(Input.Keys.W);
                    play.render(.1f);
                    require(play.snapshot().players().getFirst().head().y() > y, "Configured movement did not reach Snake");
                    key(Input.Keys.P);
                    require(play.snapshot().state() == SnakeGame.State.PAUSED, "P did not pause");
                    long tick = play.snapshot().tick();
                    key(Input.Keys.D);
                    play.render(.1f);
                    require(play.snapshot().tick() == tick, "Paused Snake accepted movement");
                    key(Input.Keys.ESCAPE);
                    require(play.snapshot().state() == SnakeGame.State.RUNNING, "Escape did not resume");
                    snapshot("snake-game.png");
                    click("return-home");
                }
                case 4 -> {
                    page(Page.HOME);
                    ((TextField) actor("search")).setText("no-such-game");
                    require(actor("empty-search") != null, "Empty search state absent");
                    snapshot("search-empty.png");
                    ((TextField) actor("search")).setText("坦克");
                    require(actor("game-tank") != null && actor("game-snake") == null, "Search did not filter");
                    click("game-tank");
                }
                case 5 -> {
                    page(Page.DETAIL); click("mode-bot");
                    require(((ArenaArt) actor("detail-art")).modeId().equals("bot"), "Tank artwork not synchronized");
                    click("start-preview");
                }
                case 6 -> {
                    page(Page.PREVIEW);
                    require(navigation().current().gameId().equals("tank") && navigation().current().modeId().equals("bot"), "Tank mode routing failed");
                    snapshot("tank-preview.png");
                    click("return-home");
                }
                case 7 -> {
                    page(Page.HOME);
                    key(Input.Keys.TAB);
                    require(ui().stage().getKeyboardFocus() != null, "Tab did not focus a control");
                    click("nav-settings");
                }
                case 8 -> {
                    page(Page.SETTINGS);
                    Slider slider = (Slider) actor("volume");
                    ui().stage().setKeyboardFocus(slider);
                    key(Input.Keys.RIGHT);
                    require(slider.getValue() == 75, "Keyboard did not adjust slider");
                    slider.setValue(35);
                    ui().stage().setKeyboardFocus(actor("resolution"));
                    click("resolution");
                    require(((SelectBox<?>) actor("resolution")).getScrollPane().getStage() != null, "Resolution dropdown did not open");
                    key(Input.Keys.ESCAPE);
                    page(Page.SETTINGS);
                    // 等待下拉列表的正常淡出动画结束，再向下方按钮发送点击。
                    ui().stage().act(.25f);
                    ui().stage().act(0f);
                    require(((SelectBox<?>) actor("resolution")).getScrollPane().getStage() == null, "Escape did not close the dropdown");
                    ui().stage().setKeyboardFocus(actor("resolution"));
                    key(Input.Keys.LEFT);
                    require(((SelectBox<?>) actor("resolution")).getSelected() == ClientSettings.Resolution.SMALL, "Resolution keyboard selection failed");
                    click("vsync");
                    click("key-up");
                    key(Input.Keys.S);
                    require(((Label) actor("settings-status")).getText().toString().contains("不能重复"), "Duplicate key was accepted");
                    key(Input.Keys.I);
                    click("save-settings");
                }
                case 9 -> {
                    require(settings().volume() == 35 && settings().keys().up() == Input.Keys.I && !settings().vsync(), "Settings not applied");
                    require(Gdx.graphics.getWidth() == 960 && Gdx.graphics.getHeight() == 540, "Saved window size not applied");
                    require(new SettingsStore(Path.of(reports.path(), "preferences.properties")).load().settings().equals(settings()), "Settings not persisted");
                    snapshot("settings.png");
                    click("cancel-settings");
                }
                case 10 -> { page(Page.HOME); click("nav-settings"); }
                case 11 -> {
                    ((Slider) actor("volume")).setValue(55);
                    click("nav-settings");
                }
                case 12 -> {
                    page(Page.SETTINGS);
                    require(((Slider) actor("volume")).getValue() == 55, "Same-page navigation discarded draft");
                    click("reset-settings");
                    click("cancel-settings");
                }
                case 13 -> {
                    page(Page.HOME);
                    require(settings().volume() == 35 && settings().keys().up() == Input.Keys.I, "Cancelled draft changed active settings");
                    Gdx.graphics.setWindowedMode(1600, 900);
                }
                case 14 -> { snapshot("home-large.png"); Gdx.graphics.setWindowedMode(800, 900); }
                case 15 -> {
                    require(ui().stage().getViewport().getScreenY() > 0, "Portrait viewport was not letterboxed");
                    snapshot("home-letterbox.png");
                    click("featured-snake");
                }
                case 16 -> { page(Page.DETAIL); click("nav-settings"); }
                case 17 -> { page(Page.SETTINGS); key(Input.Keys.ESCAPE); }
                case 18 -> {
                    page(Page.DETAIL);
                    Gdx.graphics.setWindowedMode(1280, 720);
                    click("start-preview");
                }
                case 19 -> {
                    page(Page.PREVIEW);
                    SnakeScreen play = (SnakeScreen) getScreen();
                    key(Input.Keys.SPACE);
                    int y = play.snapshot().players().getFirst().head().y();
                    key(Input.Keys.W);
                    play.render(.1f);
                    require(play.snapshot().players().getFirst().head().y() == y, "Old direction binding still active after rebinding");
                    key(Input.Keys.I);
                    play.render(.1f);
                    require(play.snapshot().players().getFirst().head().y() > y, "New direction binding did not take effect");
                    click("return-home");
                }
                case 20 -> {
                    page(Page.HOME);
                    ui().stage().setKeyboardFocus(actor("game-snake"));
                    key(Input.Keys.ENTER);
                }
                case 21 -> {
                    page(Page.DETAIL);
                    ui().stage().setKeyboardFocus(actor("back"));
                    key(Input.Keys.ENTER);
                }
                case 22 -> { page(Page.HOME); snapshot("home-final.png"); glVersion = Gdx.gl.glGetString(GL20.GL_VERSION); finished = true; click("exit"); }
                default -> throw new IllegalStateException("Scenario did not exit");
            }
        }

        private void failureStep() {
            switch (step++) {
                case 0 -> click("nav-settings");
                case 1 -> {
                    page(Page.SETTINGS);
                    ui().stage().setKeyboardFocus(actor("resolution"));
                    key(Input.Keys.LEFT);
                    ((Slider) actor("volume")).setValue(5);
                    click("save-settings");
                }
                case 2 -> {
                    require(settings().equals(ClientSettings.defaults()), "Failed save changed active settings");
                    require(Gdx.graphics.getWidth() == 1280 && Gdx.graphics.getHeight() == 720, "Failed save did not restore window");
                    require(((Label) actor("settings-status")).getText().toString().contains("保存失败"), "Save error not displayed");
                    snapshot("settings-error.png");
                    finished = true;
                    click("exit");
                }
                default -> throw new IllegalStateException("Error scenario did not exit");
            }
        }

        private UiScreen ui() { return (UiScreen) getScreen(); }
        private Actor actor(String name) { return ui().stage().getRoot().findActor(name); }
        private void page(Page page) { require(navigation().current().page() == page, "Wrong page, expected " + page); }
        private void key(int key) {
            Gdx.input.getInputProcessor().keyDown(key);
            Gdx.input.getInputProcessor().keyUp(key);
        }

        private void click(String name) {
            Actor target = actor(name);
            require(target != null, "Control not found: " + name);
            Vector2 point = target.localToStageCoordinates(new Vector2(target.getWidth() / 2, target.getHeight() / 2));
            Actor hit = ui().stage().hit(point.x, point.y, true);
            if (!(hit == target || (hit != null && hit.isDescendantOf(target)))) {
                snapshot("unreachable-" + name + ".png");
                throw new IllegalStateException("Control not reachable: " + name + ", hit=" + hit
                        + ", at=" + point + ", target bounds=" + target.getX() + "," + target.getY()
                        + "," + target.getWidth() + "," + target.getHeight());
            }
            ui().stage().stageToScreenCoordinates(point);
            Gdx.input.getInputProcessor().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
            Gdx.input.getInputProcessor().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        }

        private boolean hittable(Actor target) {
            Vector2 point = target.localToStageCoordinates(new Vector2(target.getWidth() / 2, target.getHeight() / 2));
            Actor hit = ui().stage().hit(point.x, point.y, true);
            return hit == target || hit != null && hit.isDescendantOf(target);
        }

        private void snapshot(String name) {
            // 先让 Button 选择新状态背景，再完成短过渡，避免截图仍停在前一状态。
            ui().render(0f);
            ui().stage().act(.2f);
            ui().render(0f);
            Pixmap image = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            try {
                java.util.Set<Integer> colors = new java.util.HashSet<>();
                for (int x = 0; x < image.getWidth(); x += 23) {
                    for (int y = 0; y < image.getHeight(); y += 23) { colors.add(image.getPixel(x, y)); }
                }
                require(colors.size() >= 8, "UI is blank or missing artwork: " + name);
                if (name.equals("home-letterbox.png")) {
                    java.util.Set<Integer> exterior = new java.util.HashSet<>();
                    for (int x = 0; x < image.getWidth(); x += 17) { exterior.add(image.getPixel(x, 16)); }
                    require(exterior.size() > 12, "Background did not cover area outside FitViewport");
                }
                PixmapIO.writePNG(reports.child(name), image, 6, true);
            } finally { image.dispose(); }
        }
    }
}
