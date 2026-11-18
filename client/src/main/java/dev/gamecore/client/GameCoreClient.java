package dev.gamecore.client;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import dev.gamecore.client.assets.ClientAssetManager;
import dev.gamecore.client.navigation.LobbyNavigation;
import dev.gamecore.client.registry.GameRegistry;
import dev.gamecore.client.screen.GameDetailScreen;
import dev.gamecore.client.screen.HomeScreen;
import dev.gamecore.client.screen.SettingsScreen;
import dev.gamecore.client.screen.SnakeScreen;
import dev.gamecore.client.screen.TankScreen;
import dev.gamecore.client.screen.OnlineTankScreen;
import dev.gamecore.client.screen.FightScreen;
import dev.gamecore.client.snake.SnakeRecords;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.client.settings.ClientSettings;
import dev.gamecore.client.settings.SettingsStore;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.client.window.GameWindowHost;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 客户端组合入口：拥有共享资源、路由和已应用设置；屏幕只负责各自的 UI。
 * 页面切换延迟到事件分发结束，避免在 Scene2D 回调内释放当前 Stage。
 */
public class GameCoreClient extends Game {
    private final SettingsStore store;
    private final Path snakeRecordsFile;
    private final GameWindowHost gameWindows;
    private final GameCoreClient owner;
    private final String windowGameId;
    private final String windowModeId;
    private final Runnable closeWindow;
    private final Runnable windowDisposed;
    private SnakeRecords snakeRecords;
    private final java.util.Map<String,SnakeRecords> onlineSnakeRecords=new java.util.HashMap<>();
    private GameRegistry registry;
    private LobbyNavigation navigation;
    private ClientSettings settings;
    private ClientAssetManager assets;
    private ThemeManager theme;
    private String startupNotice = "";
    private boolean transitionPending;private boolean platformRoute;
    private final dev.gamecore.client.platform.PlatformSession platformSession=new dev.gamecore.client.platform.PlatformSession();
    private final dev.gamecore.client.traffic.TrafficMonitor trafficMonitor=new dev.gamecore.client.traffic.TrafficMonitor();
    private dev.gamecore.client.traffic.TrafficOverlay trafficOverlay;
    { platformSession.observer(trafficMonitor::exchange); }
    private boolean disposed;
    private Consumer<Runnable> windowScheduler = action -> Gdx.app.postRunnable(action);

    public GameCoreClient() {
        this(defaultSettingsFile(), null);
    }
    public GameCoreClient(GameWindowHost gameWindows) { this(defaultSettingsFile(), gameWindows); }
    private static Path defaultSettingsFile() {
        return Path.of(System.getProperty("gamecore.settingsFile",
                Path.of(System.getProperty("user.home"), ".gamecore", "settings.properties").toString()));
    }

    /** 可注入路径用于隔离自动测试，不读取或覆盖用户的真实设置。 */
    public GameCoreClient(Path settingsFile) { this(settingsFile, null); }

    public GameCoreClient(Path settingsFile, GameWindowHost gameWindows) {
        store = new SettingsStore(settingsFile);
        snakeRecordsFile = settingsFile.toAbsolutePath().resolveSibling("snake-records.properties");
        this.gameWindows = gameWindows;
        owner = null; windowGameId = null; windowModeId = null; closeWindow = null; windowDisposed = null;
    }

    protected GameCoreClient(GameCoreClient owner, String gameId, String modeId, Runnable closeWindow, Runnable windowDisposed) {
        this.owner = owner;
        store = owner.store; snakeRecordsFile = owner.snakeRecordsFile; gameWindows = null;
        windowGameId = gameId; windowModeId = modeId;
        this.closeWindow = closeWindow; this.windowDisposed = windowDisposed;
    }

    /** 每个窗口独立持有 Stage/字体/纹理，仅共享同一线程上的成绩服务，避免互相释放 GPU 资源。 */
    public static GameCoreClient gameWindow(GameCoreClient owner, String gameId, String modeId,
                                           Runnable closeWindow, Runnable windowDisposed) {
        owner.registry().require(gameId).requireMode(modeId);
        return new GameCoreClient(owner, gameId, modeId, closeWindow, windowDisposed);
    }

    @Override
    public void create() {
        registry = owner == null ? GameRegistry.builtIn() : owner.registry;
        navigation = new LobbyNavigation(registry);
        snakeRecords = owner == null ? new SnakeRecords(snakeRecordsFile) : owner.snakeRecords;
        if (owner == null) {
            SettingsStore.LoadResult loaded = store.load();
            settings = loaded.settings();
            startupNotice = loaded.notice();
            if (!Gdx.graphics.setWindowedMode(settings.resolution().width(), settings.resolution().height())) {
                startupNotice = "当前显示环境未能应用已保存的窗口尺寸。";
            }
        } else {
            settings = owner.settings;
            navigation.preview(windowGameId, windowModeId);
        }
        Gdx.graphics.setVSync(settings.vsync());
        assets = createAssets();
        theme = new ThemeManager(assets);
        trafficOverlay = new dev.gamecore.client.traffic.TrafficOverlay(traffic(), () -> platform().profile(), assets);
        if(owner==null&&platformSession.autoOpen()){
            platformRoute=true;setScreen(new dev.gamecore.client.screen.PlatformScreen(this));
        }else{setScreen(owner == null ? new HomeScreen(this) : createGameScreen(windowGameId, windowModeId));}
    }

    public GameRegistry registry() { return registry; }
    public LobbyNavigation navigation() { return navigation; }
    public ClientSettings settings() { return settings; }
    public ClientAssetManager assets() { return assets; }
    public ThemeManager theme() { return theme; }
    /** 仅用于窗口资源装配及故障验收；正常客户端始终使用打包的背景。 */
    protected ClientAssetManager createAssets() { return new ClientAssetManager(); }
    public String startupNotice() { return startupNotice; }
    public SnakeRecords snakeRecords() { return snakeRecords; }
    /** 在线成绩独立保存且按场景分榜，复用原记分器，不污染本地模式纪录。 */
    public SnakeRecords onlineSnakeRecords(String map){
        if(!java.util.Set.of("clear","obstacles").contains(map)){throw new IllegalArgumentException("Snake arena");}
        if(owner!=null){return owner.onlineSnakeRecords(map);}
        return onlineSnakeRecords.computeIfAbsent(map,key->new SnakeRecords(snakeRecordsFile.resolveSibling("snake-online-"+key+"-records.properties")));
    }
    public boolean isDisposed() { return disposed; }
    public boolean isGameWindow() { return owner != null; }
    /** 多窗口必须在所属窗口上下文处理切页，不能让全局 postRunnable 误用另一个窗口的输入/尺寸。 */
    public void setWindowScheduler(Consumer<Runnable> scheduler) { windowScheduler = java.util.Objects.requireNonNull(scheduler); }

    public dev.gamecore.client.platform.PlatformSession platform(){return owner==null?platformSession:owner.platform();}
    /** 记录归主窗口所有，各窗口的面板读取同一份；绘制资源按窗口分开。 */
    public dev.gamecore.client.traffic.TrafficMonitor traffic(){return owner==null?trafficMonitor:owner.traffic();}
    public dev.gamecore.client.traffic.TrafficOverlay trafficOverlay(){return trafficOverlay;}
    /** 新连接的旁路；只有开发账户已登录时才非空。 */
    public dev.gamecore.network.transport.TrafficObserver trafficObserver(){return traffic().observer(platform().profile());}
    public void openPlatform(){customScreen(()->new dev.gamecore.client.screen.PlatformScreen(this),true);}
    public void platformPlay(dev.gamecore.platform.api.PlatformMessages.Ticket ticket){customScreen(()->new dev.gamecore.client.screen.OnlineArenaScreen(this,ticket.gameId(),ticket),true);}
    private void customScreen(java.util.function.Supplier<UiScreen> next,boolean custom){
        if(transitionPending||disposed){return;}transitionPending=true;windowScheduler.accept(()->{try{if(disposed){return;}var screen=next.get();var previous=getScreen();setScreen(screen);platformRoute=custom;if(previous!=null){previous.dispose();}}finally{transitionPending=false;}});
    }
    public void home() { if(platformRoute){navigation.home();customScreen(()->new HomeScreen(this),false);return;} if (isGameWindow()) { closeWindow.run(); } else { navigate(navigation::home); } }
    public void detail(String gameId) { navigate(() -> navigation.detail(gameId)); }
    public void preview(String gameId, String modeId) {
        registry.require(gameId).requireMode(modeId);
        if (gameWindows != null) { gameWindows.open(this, gameId, modeId); }
        else { navigate(() -> navigation.preview(gameId, modeId)); }
    }
    public void openSettings() { navigate(navigation::settings); }
    public void back() { if(platformRoute){home();return;} if (isGameWindow()) { closeWindow.run(); } else { navigate(navigation::back); } }
    public void exit() { if (isGameWindow()) { closeWindow.run(); } else { Gdx.app.exit(); } }

    private void navigate(Runnable change) {
        if (transitionPending || disposed) { return; }
        transitionPending = true;
        windowScheduler.accept(() -> {
            try {
                if (disposed) { return; }
                LobbyNavigation.Destination before = navigation.current();
                change.run();
                LobbyNavigation.Destination destination = navigation.current();
                if (destination.equals(before)) { return; }
                UiScreen next = switch (destination.page()) {
                    case HOME -> new HomeScreen(this);
                    case DETAIL -> new GameDetailScreen(this, registry.require(destination.gameId()));
                    case SETTINGS -> new SettingsScreen(this);
                    case PREVIEW -> createGameScreen(destination.gameId(), destination.modeId());
                };
                Screen previous = getScreen();
                setScreen(next);
                if (previous != null) { previous.dispose(); }
            } finally {
                transitionPending = false;
            }
        });
    }

    /** 显式路由防止新增游戏被默认错误派发到 Tank 页面；大厅与独立窗口复用同一入口。 */
    private UiScreen createGameScreen(String gameId, String modeId) {
        if ("multiplayer".equals(modeId)) { return new dev.gamecore.client.screen.OnlineArenaScreen(this, gameId); }
        return switch (gameId) {
            case "snake" -> new SnakeScreen(this, modeId);
            case "tank" -> "online".equals(modeId) ? new OnlineTankScreen(this) : new TankScreen(this, modeId);
            case "fight" -> new FightScreen(this, modeId);
            default -> throw new IllegalArgumentException("No game screen: " + gameId);
        };
    }

    /** 只有窗口应用和持久化都成功才替换生效快照；失败时回滚窗口和 VSync。 */
    public String applySettings(ClientSettings candidate) {
        int oldWidth = Gdx.graphics.getWidth();
        int oldHeight = Gdx.graphics.getHeight();
        try {
            if (!Gdx.graphics.setWindowedMode(candidate.resolution().width(), candidate.resolution().height())) {
                return "当前显示环境不支持这个窗口尺寸。";
            }
            Gdx.graphics.setVSync(candidate.vsync());
            store.save(candidate);
            settings = candidate;
            startupNotice = "";
            return "";
        } catch (IOException | RuntimeException exception) {
            Gdx.graphics.setWindowedMode(oldWidth, oldHeight);
            Gdx.graphics.setVSync(settings.vsync());
            Gdx.app.error("GameCore", "Could not apply preferences", exception);
            return "保存失败，请检查设置目录的写入权限。";
        }
    }

    @Override public void resize(int width, int height) {
        super.resize(width, height);
        if (trafficOverlay != null) { trafficOverlay.resize(width, height); }
    }

    /** 所有 Stage 先释放，共享字体、纹理和音频最后释放。重复 dispose 不重复销毁。 */
    @Override
    public void dispose() {
        if (disposed) { return; }
        disposed = true;
        if(owner==null){platformSession.closeAsync();}
        Screen current = getScreen();
        setScreen(null);
        if (current != null) { current.dispose(); }
        if (trafficOverlay != null) { trafficOverlay.dispose(); }
        if (snakeRecords != null) { snakeRecords.flush(); }
        if(owner==null){onlineSnakeRecords.values().forEach(SnakeRecords::flush);}
        if (assets != null) { assets.dispose(); }
        if (windowDisposed != null) { windowDisposed.run(); }
    }
}
