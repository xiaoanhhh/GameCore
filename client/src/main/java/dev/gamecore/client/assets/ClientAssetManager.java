package dev.gamecore.client.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;
import dev.gamecore.client.ui.UiFontScale;
import dev.gamecore.client.fight.FightSpriteLayout;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 小规模共享 UI 资源的唯一所有者；屏幕只借用，不重复加载或释放字体和纹理。 */
public final class ClientAssetManager implements Disposable {
    private final List<Disposable> owned = new ArrayList<>();
    private final Map<String, Texture> fighterSheets = new java.util.HashMap<>();
    public BitmapFont small;
    public BitmapFont body;
    public BitmapFont heading;
    public BitmapFont title;
    public BitmapFont hero;
    public final Texture pixel;
    public final Texture circle;
    public final Texture fighters;
    public final Texture shade;
    /** 径向柔光（中心不透明→边缘透明），大厅背景光晕用；按窗口共享，退出统一释放。 */
    public final Texture glow;
    public final Texture background;
    public final NinePatch rounded;
    public final NinePatch tileRounded;
    /** 大厅切角九宫格：右上与左下各切 6 逻辑像素，按钮/卡片/输入框共用。 */
    public final NinePatch cut;
    private Sound chime;
    private boolean disposed;
    private Texture fightMaterials;
    private boolean failedMaterials;
    private FontSet fonts;
    private BitmapFont fightDisplay;
    private float fightDisplayDensity;
    private final Map<dev.gamecore.fight.FightMap,Texture> fightStages = new java.util.EnumMap<>(dev.gamecore.fight.FightMap.class);
    private final java.util.Set<dev.gamecore.fight.FightMap> failedStages = java.util.EnumSet.noneOf(dev.gamecore.fight.FightMap.class);
    private final UiFontScale fontScale;
    private float failedDensity = Float.NaN;

    public ClientAssetManager() { this("backgrounds/lobby-moon-garden-v1.png"); }

    /** 背景路径可注入以验收加载失败；可选背景失败不阻止设置和游戏正常使用。 */
    public ClientAssetManager(String backgroundPath) {
        float initialDensity = UiFontScale.densityFor(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        fontScale = new UiFontScale(initialDensity);
        try {
            install(createFonts(initialDensity));
            pixel = texture(1, false);
            circle = texture(64, true);
            fighters = own(new Texture(Gdx.files.internal(FightSpriteLayout.ASSET)));
            fighters.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            Pixmap gradient = new Pixmap(1, 128, Pixmap.Format.RGBA8888);
            try {
                for (int y = 0; y < 128; y++) {
                    gradient.setColor(1, 1, 1, y / 127f);
                    gradient.drawPixel(0, y);
                }
                shade = own(new Texture(gradient));
                shade.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            } finally { gradient.dispose(); }
            Pixmap radial = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
            try {
                radial.setBlending(Pixmap.Blending.None);
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        float d = Math.min(1, (float) Math.hypot(x - 63.5, y - 63.5) / 63.5f);
                        float falloff = (1 - d) * (1 - d);
                        radial.setColor(1, 1, 1, falloff * falloff * (3 - 2 * falloff));
                        radial.drawPixel(x, y);
                    }
                }
                glow = own(new Texture(radial));
                glow.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            } finally { radial.dispose(); }
            Texture loadedBackground = null;
            try {
                loadedBackground = own(new Texture(Gdx.files.internal(backgroundPath)));
                loadedBackground.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            } catch (RuntimeException exception) {
                Gdx.app.log("GameCore", "Background unavailable; using theme color: " + backgroundPath);
            }
            background = loadedBackground;
            // 圆角纹理使用四倍像素，再还原逻辑尺寸，避免放大后把低分辨率边缘拉伸。
            int factor = 4;
            int extent = 32 * factor;
            int radius = 8 * factor;
            Pixmap patch = new Pixmap(extent, extent, Pixmap.Format.RGBA8888);
            try {
                patch.setColor(Color.WHITE);
                patch.fillRectangle(radius, 0, extent - 2 * radius, extent);
                patch.fillRectangle(0, radius, extent, extent - 2 * radius);
                for (int x : new int[]{radius, extent - radius - 1}) {
                    for (int y : new int[]{radius, extent - radius - 1}) { patch.fillCircle(x, y, radius); }
                }
                Texture texture = own(new Texture(patch));
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                rounded = new NinePatch(texture, 10 * factor, 10 * factor, 10 * factor, 10 * factor);
                rounded.scale(1f / factor, 1f / factor);
                tileRounded = new NinePatch(rounded);
                tileRounded.scale(.35f, .35f);
            } finally {
                patch.dispose();
            }
            int chamfer = 6 * factor;
            Pixmap angled = new Pixmap(extent, extent, Pixmap.Format.RGBA8888);
            try {
                angled.setColor(Color.WHITE);
                angled.fill();
                angled.setBlending(Pixmap.Blending.None);
                angled.setColor(0, 0, 0, 0);
                // Pixmap 原点在左上：先切右上角，再切左下角，其余两角保持直角。
                angled.fillTriangle(extent - chamfer, 0, extent, 0, extent, chamfer);
                angled.fillTriangle(0, extent - chamfer, 0, extent, chamfer, extent);
                Texture texture = own(new Texture(angled));
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                cut = new NinePatch(texture, 10 * factor, 10 * factor, 10 * factor, 10 * factor);
                cut.scale(1f / factor, 1f / factor);
            } finally {
                angled.dispose();
            }
        } catch (RuntimeException exception) {
            dispose();
            throw new IllegalStateException("GameCore UI resources could not be loaded", exception);
        }
        try {
            chime = own(Gdx.audio.newSound(Gdx.files.internal("audio/preview.wav")));
        } catch (RuntimeException exception) {
            Gdx.app.log("GameCore", "Audio preview unavailable: " + exception.getClass().getSimpleName());
        }
    }

    private BitmapFont font(FreeTypeFontGenerator generator, String characters, int logicalSize, float density) {
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = UiFontScale.rasterSize(logicalSize, density);
        parameter.characters = characters;
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;
        if (logicalSize >= 32) {
            // 字体子集只有单一字重：标题级字号用同色细描边模拟中粗体，拉开与正文的层级；颜色仍由 Label 着色。
            parameter.borderWidth = .45f * density;
            parameter.borderColor = Color.WHITE;
        }
        BitmapFont result = generator.generateFont(parameter);
        result.getData().setScale(logicalSize / (float) parameter.size);
        result.setUseIntegerPositions(false);
        return result;
    }

    private FontSet createFonts(float density) {
        List<BitmapFont> generated = new ArrayList<>();
        String fontFile = Gdx.files.internal("fonts/font-file.txt").readString("UTF-8").strip();
        if (!fontFile.matches("GameCoreUISans-[0-9a-f]{16}\\.ttf")) { throw new IllegalStateException("Invalid font manifest"); }
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/" + fontFile));
        try {
            String characters = Gdx.files.internal("fonts/characters.txt").readString("UTF-8");
            for (int size : new int[]{14, 18, 24, 32, 40}) { generated.add(font(generator, characters, size, density)); }
            return new FontSet(density, List.copyOf(generated));
        } catch (RuntimeException exception) {
            generated.forEach(BitmapFont::dispose);
            throw exception;
        } finally { generator.dispose(); }
    }

    private void install(FontSet next) {
        fonts = next;
        small = next.values().get(0);
        body = next.values().get(1);
        heading = next.values().get(2);
        title = next.values().get(3);
        hero = next.values().get(4);
    }

    public float fontDensity() { return fonts.density(); }
    /** 格斗标题独立使用粗字重，仅在需要时栅格化；随既有密度切换，替换后释放旧图集。 */
    public BitmapFont fightDisplay() {
        if (fightDisplay != null && fightDisplayDensity == fonts.density()) { return fightDisplay; }
        String file = Gdx.files.internal("fonts/display-font-file.txt").readString("UTF-8").strip();
        if (!file.matches("GameCoreDisplay-[0-9a-f]{16}\\.ttf")) { throw new IllegalStateException("Invalid display font manifest"); }
        var generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/" + file));
        BitmapFont next;
        try {
            String glyphs = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 /:!.-+Ⅱ·→←↑↓|"
                    + "终极格斗奶蛙肥嘟雷霆风暴蛛网爆发前手刺拳后直横向摆无畏登场准备战开始已暂停继续对决选择角色获胜本局平时间到"
                    + "长按疾跑连二段跳下穿台出三击人机竞技地双能量生命远程大招冲刺格挡满力觉醒热血全开见面礼请多指教接得住吗来痛快打吧跳过开场闪电波蜘蛛丝"
                    + "霓虹天城云巅武馆熔火铸场功夫庆祝胜利地图宽踏步飘花海高抗热浪余烬"
                    + "你的士鼠标切换焦点确认返预览键定掌控本阵古刹武境炉追均衡都市选相同下一步可回布或近捧腹大笑哈"
                    + "屋顶连廊青瓦飞檐石柱盖吊架铁竹影溪谷楼栈道霜月遗迹冰封断桥古殿残景落脚点摩悬空奥义"
                    + "九引八方狱觉醒落聚丝茧膨胀锁点空裂时传送袭敌冷却不足散去";
            next = font(generator, glyphs, 96, fonts.density());
        } finally { generator.dispose(); }
        if (fightDisplay != null) { fightDisplay.dispose(); }
        fightDisplay = next; fightDisplayDensity = fonts.density(); return fightDisplay;
    }
    /** 舞台美术为可选装饰，缺失回退程序场景；不影响规则、平台或输入。 */
    public Texture fightStage() {
        return fightStage(dev.gamecore.fight.FightMap.NEON);
    }
    /** 地图背景按需缓存，选图页加载全部缩略图，切回复用；窗口关闭统一释放。 */
    public Texture fightStage(dev.gamecore.fight.FightMap map) {
        if (!fightStages.containsKey(map) && !failedStages.contains(map)) {
            try { var texture=own(new Texture(Gdx.files.internal(dev.gamecore.client.fight.FightMapStyle.of(map).background)));texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);fightStages.put(map,texture); }
            catch(RuntimeException failure){failedStages.add(map);Gdx.app.error("Fight","Stage artwork unavailable; using procedural stage",failure);}
        }
        return fightStages.get(map);
    }

    /** 建筑材质全图只缓存一份，各地图/预览借用；失败仍可画程序建筑，退出由 owned 释放。 */
    public Texture fightSceneryMaterials(){
        if(fightMaterials==null&&!failedMaterials){try{fightMaterials=own(new Texture(Gdx.files.internal(dev.gamecore.client.fight.FightSceneryMaterial.ASSET)));fightMaterials.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);}
            catch(RuntimeException failure){failedMaterials=true;Gdx.app.error("Fight","Scenery materials unavailable; using procedural surfaces",failure);}}
        return fightMaterials;
    }
    /** 先生成新资源，调用方更新完整页面后 close 才释放旧资源，避免样式悬挂。 */
    public FontChange refreshFonts(int backBufferWidth, int backBufferHeight, float delta) {
        float desired = fontScale.advance(backBufferWidth, backBufferHeight, delta);
        if (desired == fonts.density() || desired == failedDensity) { return null; }
        FontSet previous = fonts;
        try {
            FontSet next = createFonts(desired);
            install(next);
            failedDensity = Float.NaN;
            return new FontChange(previous, next);
        } catch (RuntimeException exception) {
            failedDensity = desired;
            Gdx.app.error("GameCore", "Could not refresh UI fonts; retaining previous atlas", exception);
            return null;
        }
    }

    private record FontSet(float density, List<BitmapFont> values) implements Disposable {
        @Override public void dispose() { values.forEach(BitmapFont::dispose); }
    }

    public static final class FontChange implements AutoCloseable {
        private final FontSet retired;
        private final Map<BitmapFont, BitmapFont> replacements = new IdentityHashMap<>();
        private boolean closed;

        private FontChange(FontSet previous, FontSet next) {
            retired = previous;
            for (int i = 0; i < previous.values().size(); i++) { replacements.put(previous.values().get(i), next.values().get(i)); }
        }

        public BitmapFont replacement(BitmapFont previous) { return replacements.getOrDefault(previous, previous); }
        @Override public void close() { if (!closed) { retired.dispose(); closed = true; } }
    }

    private Texture texture(int size, boolean round) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(Color.WHITE);
            if (round) { pixmap.fillCircle(size / 2, size / 2, size / 2 - 1); }
            else { pixmap.fill(); }
            Texture result = own(new Texture(pixmap));
            result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return result;
        } finally {
            pixmap.dispose();
        }
    }

    private <T extends Disposable> T own(T resource) {
        owned.add(resource);
        return resource;
    }

    public boolean audioAvailable() { return chime != null; }

    /** 动作图集按窗口惰性缓存，Snake/Tank 不额外加载三张纹理；退出时仍由 owned 统一释放。 */
    public Texture fighterSheet(String path) {
        Texture existing = fighterSheets.get(path);
        if (existing != null) { return existing; }
        Texture loaded = own(new Texture(Gdx.files.internal(path)));
        loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear); fighterSheets.put(path, loaded); return loaded;
    }

    public void previewVolume(int volume) {
        if (chime != null) { chime.play(volume / 100f); }
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            if (fonts != null) { fonts.dispose(); }
            if (fightDisplay != null) { fightDisplay.dispose(); fightDisplay = null; }
            for (int i = owned.size() - 1; i >= 0; i--) { owned.get(i).dispose(); }
            owned.clear();
        }
    }
}
