package dev.gamecore.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.client.ui.ArenaArt;
import com.badlogic.gdx.Input;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

/** 放大窗口后直接检查字体纹理是否仍被放大，不依赖“截图看起来正常”的单一判断。 */
public final class UiScaleSmokeTest {
    private UiScaleSmokeTest() { }

    public static void main(String[] args) throws IOException {
        Path report = Path.of(args[0]);
        Files.createDirectories(report);
        Files.deleteIfExists(report.resolve("result.txt"));
        Files.deleteIfExists(report.resolve("preferences.properties"));
        Probe client = new Probe(report);
        Lwjgl3ApplicationConfiguration configuration = Lwjgl3Launcher.configuration(DesktopSettings.defaults());
        configuration.disableAudio(true);
        new Lwjgl3Application(client, configuration);
        require(client.finished && client.isDisposed(), "Scale test did not complete");
        String status = Boolean.getBoolean("gamecore.scale.captureOnly") ? "UI_SCALE_CAPTURE_DONE" : "UI_SCALE_TEST_OK";
        Files.writeString(report.resolve("result.txt"), status + "\n" + client.metrics);
        System.out.println(status);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    private static final class Probe extends GameCoreClient {
        private static final int[][] SIZES = {{1280,720}, {1600,900}, {1920,1080}, {2560,1440}, {2557,1497}, {3840,2160},
                {1280,720},{2557,1497},{1600,900},{960,540},{1280,720}};
        private final FileHandle report;
        private final StringBuilder metrics = new StringBuilder();
        private int frames;
        private int step;
        private boolean finished;
        private int stateStep;
        private BitmapFont previousBody;
        private UiScreen retainedScreen;
        private Object retainedInput;
        private TextField retainedSearch;
        private final Map<String,Rectangle> homeGeometry=new LinkedHashMap<>();
        private float signatureHeight;
        private boolean maximizeRequested,maximizeVerified;

        private Probe(Path directory) {
            super(directory.resolve("preferences.properties"));
            report = new FileHandle(directory.toFile());
        }

        @Override public void render() {
            super.render();
            if (++frames % 24 != 0) { return; }
            if (step / 2 >= SIZES.length) {
                if (Boolean.getBoolean("gamecore.scale.captureOnly")) { finished = true; exit(); }
                else if(!maximizeRequested){
                    ((Lwjgl3Graphics)Gdx.graphics).getWindow().maximizeWindow();maximizeRequested=true;
                }else if(!maximizeVerified){
                    var window=((Lwjgl3Graphics)Gdx.graphics).getWindow();
                    require(GLFW.glfwGetWindowAttrib(window.getWindowHandle(),GLFW.GLFW_MAXIMIZED)==GLFW.GLFW_TRUE,"Lobby did not maximize");
                    TextField search=((UiScreen)getScreen()).stage().getRoot().findActor("search");
                    search.setText("tank");search.setText("");getScreen().render(0);
                    metrics.append("HighDensityCardCreation=true\n");
                    verifyHomeGeometry();snapshot("home-maximized.png");
                    metrics.append("NativeMaximized=").append(Gdx.graphics.getBackBufferWidth()).append('x').append(Gdx.graphics.getBackBufferHeight()).append('\n');
                    window.restoreWindow();
                    maximizeVerified=true;
                }else { verifyState(); }
                return;
            }
            int[] size = SIZES[step / 2];
            if (step++ % 2 == 0) {
                require(Gdx.graphics.setWindowedMode(size[0], size[1]), "Cannot resize test window");
                return;
            }
            int width = Gdx.graphics.getBackBufferWidth();
            int height = Gdx.graphics.getBackBufferHeight();
            float physicalScale = Math.min(width / 1280f, height / 720f);
            float magnification = physicalScale * assets().body.getData().scaleX;
            if (!Boolean.getBoolean("gamecore.scale.captureOnly")) {
                require(magnification <= 1.001f, "Small font texture is still magnified: " + magnification);
                verifyFontReferences(((UiScreen) getScreen()).stage().getRoot());
                verifyRetiredFont();
                verifyHomeGeometry();
            }
            metrics.append("Requested=").append(size[0]).append('x').append(size[1])
                    .append(", Logical=").append(Gdx.graphics.getWidth()).append('x').append(Gdx.graphics.getHeight())
                    .append(", Framebuffer=").append(width).append('x').append(height)
                    .append(", FontScale=").append(assets().body.getData().scaleX)
                    .append(", FontTextureMagnification=").append(magnification).append('\n');
            previousBody = assets().body;
            snapshot("home-" + size[0] + "x" + size[1] + ".png");
        }

        /** 纹理清晰并不代表排版正确：直接测量侧栏/卡片与实际字形，反复换密度也不得撑出原有热区。 */
        private void verifyHomeGeometry(){
            try{checkHomeGeometry();}catch(RuntimeException failure){snapshot("layout-failure.png");throw failure;}
        }
        private void checkHomeGeometry(){
            var ui=(UiScreen)getScreen();
            var topbar=ui.stage().getRoot().findActor("lobby-topbar");
            for(String name:new String[]{"lobby-brand","lobby-signature"}){inside(ui.stage().getRoot().findActor(name),topbar,name);}
            var signature=(Label)ui.stage().getRoot().findActor("lobby-signature");
            float height=signature.getGlyphLayout().height;
            if(signatureHeight==0){signatureHeight=height;}else{require(Math.abs(height-signatureHeight)<2,"Sidebar caption changed logical height: "+height+" vs "+signatureHeight);}
            for(String game:new String[]{"snake","tank","fight"}){
                var card=ui.stage().getRoot().findActor("game-"+game);var cardBounds=bounds(card);
                require(cardBounds.x>=-1&&cardBounds.y>=-1&&cardBounds.x+cardBounds.width<=ui.stage().getWidth()+1&&cardBounds.y+cardBounds.height<=ui.stage().getHeight()+1,"Card outside viewport: "+game);
                for(String part:new String[]{"cover","copy","summary"}){inside(ui.stage().getRoot().findActor("game-"+part+"-"+game),card,part+" "+game);}
                var summary=(Label)ui.stage().getRoot().findActor("game-summary-"+game);
                require(summary.getGlyphLayout().height<=summary.getHeight()+1,"Summary text overflow "+game);
                require(summary.getGlyphLayout().width<=summary.getWidth()+1,"Summary line overflow "+game);
                var xy=card.localToStageCoordinates(new Vector2(card.getWidth()/2,card.getHeight()/2));var hit=ui.stage().hit(xy.x,xy.y,true);
                require(hit==card||hit!=null&&hit.isDescendantOf(card),"Scaled card is not reachable: "+game);
            }
            for(String name:new String[]{"lobby-topbar","lobby-brand","lobby-signature","search","featured-snake","game-snake","game-tank","game-fight"}){
                var actor=ui.stage().getRoot().findActor(name);var rect=bounds(actor);
                var before=homeGeometry.putIfAbsent(name,rect);
                if(before!=null){require(Math.abs(rect.x-before.x)<4&&Math.abs(rect.y-before.y)<4&&Math.abs(rect.width-before.width)<4&&Math.abs(rect.height-before.height)<4,
                        "Logical control bounds drifted: "+name+" "+before+" -> "+rect);}
            }
            metrics.append("GeometryChecked=").append(Gdx.graphics.getBackBufferWidth()).append('x').append(Gdx.graphics.getBackBufferHeight())
                    .append(", SignatureHeight=").append(height).append(", AllThreeCardsReachable=true\n");
        }
        private Rectangle bounds(Actor actor){require(actor!=null,"Missing layout control");var xy=actor.localToStageCoordinates(new Vector2());return new Rectangle(xy.x,xy.y,actor.getWidth(),actor.getHeight());}
        private void inside(Actor child,Actor parent,String name){var c=bounds(child);var p=bounds(parent);require(c.x>=p.x-1&&c.y>=p.y-1&&c.x+c.width<=p.x+p.width+1&&c.y+c.height<=p.y+p.height+1,"Scaled content outside container: "+name+" "+c+" parent="+p);}

        private void verifyRetiredFont() {
            if (previousBody != null && previousBody != assets().body) {
                for (var region : previousBody.getRegions()) {
                    require(region.getTexture().getTextureObjectHandle() == 0, "Old font atlas was not released");
                }
            }
        }

        private void verifyFontReferences(Actor actor) {
            var fonts = java.util.List.of(assets().small, assets().body, assets().heading, assets().title, assets().hero);
            if (actor instanceof Label label) { require(fonts.contains(label.getStyle().font), "Label retains old font"); }
            if (actor instanceof TextButton button) { require(fonts.contains(button.getStyle().font), "Button retains old font"); }
            if (actor instanceof TextField field) { require(fonts.contains(field.getStyle().font), "Field retains old font"); }
            if (actor instanceof SelectBox<?> box) {
                require(fonts.contains(box.getStyle().font), "SelectBox retains old font");
                require(fonts.contains(box.getList().getStyle().font), "Hidden popup list retains old font");
            }
            if (actor instanceof Group group) {
                for (Actor child : group.getChildren()) { verifyFontReferences(child); }
            }
        }

        private void verifyState() {
            switch (stateStep++) {
                case 0 -> {
                    retainedScreen = (UiScreen) getScreen();
                    retainedInput = Gdx.input.getInputProcessor();
                    retainedSearch = retainedScreen.stage().getRoot().findActor("search");
                    retainedSearch.setText("tank");
                    retainedSearch.setSelection(1, 3);
                    retainedScreen.stage().setKeyboardFocus(retainedSearch);
                    Gdx.graphics.setWindowedMode(1920, 1080);
                }
                case 1 -> {
                    require(getScreen() == retainedScreen, "Resize rebuilt the page");
                    require(retainedScreen.stage().getKeyboardFocus() == retainedSearch, "Resize lost keyboard focus");
                    require(retainedSearch.getText().equals("tank") && retainedSearch.getSelection().equals("an"), "Resize lost search or selection");
                    verifyFontReferences(retainedScreen.stage().getRoot());
                    Gdx.graphics.setWindowedMode(960, 540);
                }
                case 2 -> {
                    require(assets().fontDensity() == 1, "Shrink did not restore native font density");
                    require(getScreen() == retainedScreen && retainedInput == Gdx.input.getInputProcessor(), "Resize changed input ownership");
                    require(retainedSearch.getSelection().equals("an"), "Shrink lost selection");
                    openSettings();
                }
                case 3 -> {
                    retainedScreen = (UiScreen) getScreen();
                    Slider volume = retainedScreen.stage().getRoot().findActor("volume");
                    volume.setValue(55);
                    previousBody = assets().body;
                    Gdx.graphics.setWindowedMode(2560, 1440);
                }
                case 4 -> {
                    require(getScreen() == retainedScreen, "Settings page was recreated");
                    Slider volume = retainedScreen.stage().getRoot().findActor("volume");
                    require(volume.getValue() == 55, "Font refresh discarded settings draft");
                    verifyFontReferences(retainedScreen.stage().getRoot());
                    verifyRetiredFont();
                    SelectBox<?> resolution = retainedScreen.stage().getRoot().findActor("resolution");
                    resolution.showScrollPane();
                }
                case 5 -> {
                    verifyFontReferences(retainedScreen.stage().getRoot());
                    snapshot("settings-scaled.png");
                    SelectBox<?> resolution = retainedScreen.stage().getRoot().findActor("resolution");
                    resolution.hideScrollPane();
                    detail("snake");
                }
                case 6 -> {
                    retainedScreen = (UiScreen) getScreen();
                    TextButton mode = retainedScreen.stage().getRoot().findActor("mode-duo");
                    retainedScreen.stage().setKeyboardFocus(mode);
                    Gdx.input.getInputProcessor().keyDown(Input.Keys.ENTER);
                    Gdx.input.getInputProcessor().keyUp(Input.Keys.ENTER);
                    Gdx.graphics.setWindowedMode(1280, 720);
                }
                case 7 -> {
                    require(getScreen() == retainedScreen, "Resize rebuilt detail page");
                    TextButton mode = retainedScreen.stage().getRoot().findActor("mode-duo");
                    ArenaArt art = retainedScreen.stage().getRoot().findActor("detail-art");
                    require(mode.isChecked() && art.modeId().equals("duo"), "Resize lost mode selection or artwork");
                    require(retainedScreen.stage().getKeyboardFocus() == mode, "Resize lost mode focus");
                    verifyFontReferences(retainedScreen.stage().getRoot());
                    snapshot("detail-mode-preserved.png");
                    metrics.append("SearchAndSelectionPreserved=true\nDraftPreserved=true\nInputPreserved=true\nOldFontTexturesReleased=true\nPopupFontsRefreshed=true\nModeAndFocusPreserved=true\nScaledGeometryPreserved=true\nGrowShrinkRepeated=true\nNativeMaximized=true\n");
                    finished = true; exit();
                }
                default -> throw new IllegalStateException("Scale state test did not exit");
            }
        }

        private void snapshot(String name) {
            int width = Gdx.graphics.getBackBufferWidth();
            int height = Gdx.graphics.getBackBufferHeight();
            Pixmap image = Pixmap.createFromFrameBuffer(0, 0, width, height);
            try {
                PixmapIO.writePNG(report.child(name), image, 6, true);
            } finally { image.dispose(); }
        }
    }
}
