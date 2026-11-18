package dev.gamecore.client.screen;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.platform.PlatformSession;
import dev.gamecore.client.ui.PlayerText;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.platform.api.PlatformHttpClient;
import dev.gamecore.platform.api.PlatformMessages.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 在线平台的只读展示与操作入口：卡片不保存业务状态，所有权限仍由平台核对。
 * HTTP 只在有界工作者中执行；离页丢弃迟到结果，Stage/焦点仅在渲染线程更新。
 */
public final class PlatformScreen extends UiScreen {
    private final PlatformSession session;
    private final Table body = new Table();
    private final Label notice, identity;
    private final ScrollPane scroll;
    private final Map<String, TextButton> tabs = new LinkedHashMap<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> new Thread(r, "platform-ui-http"), new ThreadPoolExecutor.AbortPolicy());
    private final ArrayBlockingQueue<Runnable> events = new ArrayBlockingQueue<>(32);
    private volatile boolean leaving;
    private boolean busy;
    private String tab = "在线大厅", pendingRoom = "", pendingGame = "";
    private long nextRefresh;
    private Label authFeedback;
    private final List<TextButton> authButtons = new ArrayList<>();
    private TextField authName, authPassword;

    public PlatformScreen(GameCoreClient app) {
        super(app, "在线平台", "Tab 切换焦点 · Enter 确认 · Esc 返回本地游戏");
        session = app.platform();
        // 页头：标题与身份/返回入口同一行；标签改为下划线式导航，节省纵向空间给房间列表。
        Table heading = new Table();
        Table copy = new Table();
        copy.add(theme.label("一起玩，才更尽兴", theme.assets.title, ThemeManager.TEXT)).left().row();
        copy.add(theme.caption("挑一款游戏，邀请朋友，开启你们的下一场对决。")).left().padTop(6);
        heading.add(copy).expandX().left().bottom();
        Table who = new Table();
        who.setBackground(theme.panel(ThemeManager.PANEL)); who.pad(6, 14, 6, 14);
        who.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(theme.dot(ThemeManager.MINT))).size(7).padRight(8);
        identity = theme.caption(""); identity.setName("platform-identity");
        who.add(identity).width(200);
        heading.add(who).right().bottom().height(36).padRight(10);
        heading.add(bind(theme.button("返回本地游戏", false), "platform-offline", app::home)).right().bottom().height(36);
        content.add(heading).growX().padBottom(14).row();
        Table navigation = new Table();
        for (String value : List.of("在线大厅", "登录", "个人主页", "好友", "战绩")) {
            TextButton control = bind(theme.nav(value, false), "platform-tab-" + value, () -> {
                if (!busy) { pendingRoom = pendingGame = ""; }
                show(value);
            });
            tabs.put(value, control);
            navigation.add(control).height(38).spaceRight(4);
        }
        notice = theme.caption("游客可浏览房间，登录后即可与朋友对战");
        notice.setWrap(true); notice.setAlignment(Align.right); notice.setName("platform-notice");
        navigation.add(notice).growX().minWidth(0).padLeft(16);
        content.add(navigation).growX().row();
        content.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(theme.solid(ThemeManager.LINE))).growX().height(1).padBottom(12).row();
        scroll = new ScrollPane(body, theme.scrollStyle());
        scroll.setName("platform-scroll"); scroll.setScrollingDisabled(true, false); scroll.setFadeScrollBars(false);
        content.add(scroll).grow().minHeight(0).row(); stage.setScrollFocus(scroll);
        show("在线大厅");
    }

    /** 所有正文按实际卡片宽度换行；长昵称与状态不会扩张按钮热区。 */
    private Label copy(String text, boolean heading) {
        Label label = heading ? theme.label(text, theme.assets.heading, ThemeManager.TEXT) : theme.caption(text);
        label.setWrap(true); label.setAlignment(Align.left); return label;
    }
    private void line(Table table, String text, boolean heading) {
        table.add(copy(text, heading)).growX().minWidth(0).left().padBottom(8).row();
    }
    private Table card(String id) {
        Table card = new Table(); card.setName(id); card.top().left();
        card.setBackground(theme.panel(ThemeManager.PANEL)); card.pad(18, 20, 18, 20); return card;
    }
    private void section(String title, String hint) {
        Table row = new Table();
        row.add(theme.label(title, theme.assets.heading, ThemeManager.TEXT)).left().expandX();
        row.add(theme.label(hint, theme.assets.small, ThemeManager.GOLD)).right();
        body.add(row).growX().padTop(14).padBottom(10).row();
    }
    private void empty(String id, String title, String detail) {
        Table card = card(id); line(card, title, true); line(card, detail, false);
        body.add(card).growX().padBottom(10).row();
    }
    private TextField field(String id, String value, int max) {
        TextField field = focus(theme.searchField(), id); field.setText(value); field.setMessageText("");
        field.setMaxLength(max); field.setFocusTraversal(false); return field;
    }
    private TextButton button(Table table, String text, String id, Runnable action) {
        return button(table, text, id, action, false);
    }
    private TextButton button(Table table, String text, String id, Runnable action, boolean primary) {
        TextButton control = bind(theme.button(text, primary), id, action);
        table.add(control).height(38).pad(4); return control;
    }
    private void clear() {
        authFeedback = null; authButtons.clear(); authName = authPassword = null;
        forgetFocus(body); body.clearChildren(); body.top().left();
    }
    private void feedback(String text) {
        notice.setText(text); if (authFeedback != null) { authFeedback.setText(text); }
    }
    private void requestState(boolean value) {
        busy = value;
        for (TextButton button : authButtons) { button.setDisabled(value); }
        for (TextButton button : tabs.values()) { button.setDisabled(value); }
        if (authName != null) { authName.setDisabled(value); }
        if (authPassword != null) { authPassword.setDisabled(value); }
    }
    private <T> void request(Callable<T> action, Consumer<T> done) { request(action, done, "正在处理，请稍候…"); }
    private <T> void request(Callable<T> action, Consumer<T> done, String pending) {
        if (busy || leaving) { return; }
        requestState(true); if (pending != null) { feedback(pending); }
        try {
            worker.execute(() -> {
                try {
                    T value = action.call();
                    offer(() -> {
                        requestState(false); feedback("操作完成");
                        try { done.accept(value); }
                        catch (RuntimeException invalid) {
                            feedback("平台返回的数据无法显示，请重试或返回本地游戏");
                            nextRefresh = System.nanoTime() + 10_000_000_000L;
                        }
                    });
                } catch (Exception failure) {
                    String code = failure instanceof PlatformHttpClient.Failure ? failure.getMessage() : "PLATFORM_UNAVAILABLE";
                    boolean expired = failure instanceof PlatformHttpClient.Failure f && f.status() == 401 && session.profile() != null;
                    if (expired) { session.expire(); }
                    offer(() -> {
                        requestState(false); nextRefresh = System.nanoTime() + 10_000_000_000L;
                        if (expired) { show("登录"); }
                        feedback(authFeedback != null && code.equals("CONFLICT")
                                ? "用户名已存在，请直接登录或更换用户名" : PlayerText.error(code));
                    });
                }
            });
        } catch (RejectedExecutionException failure) { requestState(false); feedback("请求过多，请稍候重试"); }
    }
    private void offer(Runnable action) { if (!leaving && !events.offer(action)) { leaving = true; } }

    private void show(String value) {
        if (busy) { return; }
        tab = value;
        if (!Set.of("登录", "在线大厅").contains(value) && session.profile() == null) { tab = "登录"; }
        clear(); scroll.setScrollY(0); nextRefresh = System.nanoTime() + 3_000_000_000L;
        for (var entry : tabs.entrySet()) {
            entry.getValue().setStyle(theme.nav(entry.getKey(), entry.getKey().equals(tab)).getStyle());
        }
        identity.setText(session.profile() == null ? "游客 · 浏览模式" : "已登录 · " + session.profile().displayName());
        // 昵称有独立宽度（页头身份胶囊内 200），超长省略，不能把标题推到页面之外。
        identity.setEllipsis(true);
        feedback(tab.equals("登录") ? "登录后即可创建房间、邀请好友和查看战绩" : "选择想玩的游戏，与朋友相约同一房间");
        switch (tab) {
            case "登录" -> { if (session.profile() == null) { login(); } else { profile(); } }
            case "个人主页" -> profile();
            case "在线大厅" -> lobby(false);
            case "好友" -> friends();
            case "战绩" -> history();
            default -> throw new IllegalStateException();
        }
    }

    private void login() {
        Table form = card("platform-auth-card");
        if (!session.configured()) {
            Label title = copy("在线服务尚未启用", true); title.setName("platform-auth-unavailable");
            form.add(title).growX().row();
            line(form, "此安装包暂时无法连接在线服务。请联系发布者获取可用版本，或先体验本地游戏。", false);
            form.getCell(button(form, "先浏览房间", "platform-browse", () -> show("在线大厅"))).left();
            body.add(form).growX().row(); feedback(session.configurationNotice()); return;
        }
        line(form, "欢迎回来，也欢迎初次见面", true);
        TextField name = field("platform-username", "", 20), password = field("platform-password", "", 72);
        authName = name; authPassword = password; name.setMessageText("输入用户名"); password.setMessageText("输入密码");
        password.setPasswordMode(true); password.setPasswordCharacter('*');
        line(form, "用户名 · 3–20 位字母、数字或下划线", false);
        form.add(name).growX().height(42).padBottom(12).row();
        line(form, "密码 · 至少 10 个字符，最多 72 字节", false);
        form.add(password).growX().height(42).row();
        authFeedback = copy("请输入账号和密码", false); authFeedback.setName("platform-auth-feedback");
        form.add(authFeedback).growX().minHeight(44).padTop(8).row();
        Table actions = new Table();
        for (boolean register : new boolean[]{false, true}) {
            authButtons.add(button(actions, register ? "注册并登录" : "登录", register ? "platform-register" : "platform-login", () -> {
                if (busy || leaving) { return; }
                String username = name.getText(), secret = password.getText();
                if (username.isBlank() || secret.isEmpty()) { feedback("请输入用户名和密码"); return; }
                password.setText("");
                request(() -> session.login(username, secret, register), p -> afterLogin(),
                        register ? "正在注册并登录，请稍候…" : "正在登录，请稍候…");
            }, !register));
        }
        form.add(actions).left().row();
        TextButton browse = button(form, "先浏览房间", "platform-browse", () -> { pendingRoom = pendingGame = ""; show("在线大厅"); });
        form.getCell(browse).left();
        Table help = card("platform-auth-help");
        line(help, pendingRoom.isEmpty() && pendingGame.isEmpty() ? "你的下一场对决，从这里开始" : "登录后继续刚才的选择", true);
        line(help, "01  选择游戏，创建或加入房间\n\n02  两位玩家点击准备\n\n03  进入比赛，尽情发挥", false);
        line(help, "密码与登录状态仅保存在本次运行的内存中。关闭客户端后，需要重新登录。", false);
        body.add(form).growX().uniformX().minWidth(0).top().padRight(14);
        body.add(help).growX().uniformX().minWidth(0).top().row();
    }

    /** 只续接玩家明确选择的创建/加入；浏览、切标签会取消旧意图。 */
    private void afterLogin() {
        String room = pendingRoom, game = pendingGame; pendingRoom = pendingGame = "";
        if (!room.isEmpty() || !game.isEmpty()) {
            tab = "在线大厅"; clear(); line(body, "已登录，正在继续你的房间选择…", false);
            button(body, "刷新大厅", "platform-refresh", () -> show("在线大厅"));
        }
        if (!room.isEmpty()) {
            request(() -> session.client().call("POST", "/api/rooms/" + room + "/join", null, Room.class),
                    r -> { session.room(r.id()); show("在线大厅"); });
        } else if (!game.isEmpty()) {
            request(() -> session.client().call("POST", "/api/rooms", new CreateRoom(game), Room.class),
                    r -> { session.room(r.id()); show("在线大厅"); });
        } else { show("在线大厅"); }
    }
    private void profile() {
        var profile = session.profile(); Table card = card("platform-profile-card");
        line(card, profile.displayName(), true); line(card, "用户名 · " + profile.username(), false);
        line(card, "修改昵称，让朋友更容易认出你", false);
        TextField rename = field("platform-name", profile.displayName(), 32);
        card.add(rename).growX().height(42).row();
        button(card, "保存昵称", "platform-rename", () -> {
            String name = rename.getText();
            request(() -> session.client().call("PUT", "/api/me", new Rename(name), Profile.class),
                    value -> { session.profile(value); show("个人主页"); feedback("昵称已保存"); });
        }, true); card.row();
        line(card, "登录信息不会保存到本机。退出登录后，仍可浏览房间和游玩本地游戏。", false);
        button(card, "退出登录", "platform-logout", () -> request(() -> { session.logout(); return true; },
                value -> { pendingRoom = pendingGame = ""; show("在线大厅"); }));
        body.add(card).growX().row(); feedback("管理你的昵称与登录状态");
    }
    private String gameName(String game) {
        try { return app.registry().require(game).name(); } catch (IllegalArgumentException unknown) { return "暂不支持的游戏"; }
    }
    private boolean supported(String game) {
        try { app.registry().require(game); return true; } catch (IllegalArgumentException unknown) { return false; }
    }

    private void lobbyTools() {
        Table tools = new Table();
        tools.add(copy(session.profile() == null ? "游客浏览 · 登录后即可创建或加入房间" : "选择游戏创建房间，或加入朋友的对局", false)).growX().minWidth(0);
        if (session.profile() == null) { button(tools, "登录 / 注册", "platform-guest-login", () -> show("登录"), true); }
        button(tools, "刷新房间", "platform-refresh", () -> show("在线大厅"));
        body.add(tools).growX().row();
    }
    private void gameCards(List<Game> games, boolean inRoom) {
        section("选择游戏", "双人在线 · 三种乐趣"); Table cards = new Table();
        for (Game game : games) {
            if (!supported(game.id())) { continue; }
            Table card = card("platform-game-" + game.id()); card.pad(8, 8, 14, 8);
            // 小封面复用游戏库的程序插画，让三款游戏一眼可辨；按钮用次要样式，信号绿实心留给“准备/进入比赛”。
            card.add(new dev.gamecore.client.ui.ArenaArt(theme, game.id(), "classic")).growX().height(72).row();
            Table copy = new Table(); copy.pad(10, 8, 0, 8);
            copy.add(theme.label(game.id().equals("snake") ? "路线与反应" : game.id().equals("tank") ? "瞄准与走位" : "出拳与连招", theme.assets.small, theme.accent(game.id()))).left().row();
            line(copy, gameName(game.id()), true);
            line(copy, app.registry().require(game.id()).summary(), false);
            card.add(copy).growX().minWidth(0).row();
            TextButton create = bind(theme.button(inRoom ? "已在房间中" : "创建房间", false), "platform-create-" + game.id(), () -> {
                if (session.profile() == null) { pendingGame = game.id(); pendingRoom = ""; show("登录"); }
                else { request(() -> session.client().call("POST", "/api/rooms", new CreateRoom(game.id()), Room.class),
                        room -> { session.room(room.id()); show("在线大厅"); }); }
            });
            create.setDisabled(inRoom); card.add().expandY().row(); card.add(create).growX().height(38).padLeft(8).padRight(8);
            cards.add(card).grow().uniformX().minWidth(0).spaceRight(14);
        }
        body.add(cards).growX().row();
    }

    private record LobbyData(Game[] games, Room[] rooms, Room mine) { }
    /** 自动刷新不清空当前列表，也不改滚动位置和键盘焦点；HTTP 请求依旧只允许一项在途。 */
    private void lobby(boolean automatic) {
        if (!automatic) { lobbyTools(); }
        if (!session.configured()) {
            empty("platform-service-unavailable", "暂时无法使用在线服务", "请联系发布者获取可用版本。你也可以返回本地游戏，即刻开玩。");
            feedback(session.configurationNotice()); nextRefresh = Long.MAX_VALUE; return;
        }
        if (session.profile() == null) {
            request(session::browse, data -> updateLobby(() -> {
                lobbyTools(); gameCards(data.games(), false); section("寻找房间", data.rooms().size() + " 个房间");
                if (data.rooms().isEmpty()) { empty("platform-empty-rooms", "还没有房间，来开第一局吧", "选择上方游戏创建房间，登录后即可邀请朋友加入。"); }
                int index = 0;
                for (RoomSummary room : data.rooms()) {
                    roomCard(room.id(), room.gameId(), room.status(), room.players(), room.capacity(), room.readyPlayers(), ++index, false, false);
                }
            }, automatic), automatic ? null : "正在查找房间…");
        } else {
            request(() -> {
                var client = session.client(); var identity = session.profile();
                Game[] games = client.call("GET", "/api/games", null, Game[].class);
                Room[] rooms = client.call("GET", "/api/rooms", null, Room[].class);
                Room mine = Arrays.stream(rooms).filter(r -> r.members().stream().anyMatch(m -> m.userId().equals(identity.userId()))).findFirst().orElse(null);
                return new LobbyData(games, rooms, mine);
            }, data -> updateLobby(() -> {
                lobbyTools(); session.room(data.mine() == null ? "" : data.mine().id());
                if (data.mine() != null) { room(data.mine()); }
                gameCards(Arrays.asList(data.games()), data.mine() != null);
                section("寻找房间", data.rooms().length + " 个房间");
                if (data.rooms().length == 0) { empty("platform-empty-rooms", "还没有房间，来开第一局吧", "选择上方游戏创建房间，等候朋友加入。"); }
                int index = 0;
                for (Room room : data.rooms()) {
                    boolean mine = data.mine() != null && room.id().equals(data.mine().id());
                    roomCard(room.id(), room.gameId(), room.status(), room.members().size(), 2,
                            (int) room.members().stream().filter(Member::ready).count(), ++index, mine, data.mine() != null);
                }
            }, automatic), automatic ? null : "正在查找房间…");
        }
    }
    private void updateLobby(Runnable build, boolean preserve) {
        float position = preserve ? scroll.getScrollY() : 0;
        Actor focused = stage.getKeyboardFocus(); String name = preserve && focused != null ? focused.getName() : null;
        clear(); build.run(); scroll.validate(); scroll.setScrollY(position); scroll.updateVisualScroll();
        if (name != null) { Actor replacement = stage.getRoot().findActor(name); if (replacement != null) { stage.setKeyboardFocus(replacement); } }
        nextRefresh = System.nanoTime() + 3_000_000_000L; feedback("房间列表已更新 · 每 3 秒自动刷新");
    }
    private void roomCard(String id, String game, String state, int players, int capacity, int ready, int index, boolean mine, boolean inRoom) {
        Table card = card("platform-room-" + id); Table text = new Table();
        line(text, gameName(game) + " · 房间 " + index, true);
        line(text, players + " / " + capacity + " 人 · " + ready + " 人已准备 · " + PlayerText.roomState(state), false);
        card.add(text).growX().minWidth(0);
        boolean joinable = supported(game) && state.equals("OPEN") && players < capacity && !inRoom;
        String action = mine ? "当前房间" : inRoom ? "请先退出房间" : !supported(game) ? "请更新客户端" : !state.equals("OPEN") ? "无法加入" : players >= capacity ? "房间已满" : session.profile() == null ? "登录后加入" : "加入房间";
        TextButton join = button(card, action, "platform-join-" + id, () -> {
            if (session.profile() == null) { pendingRoom = id; pendingGame = ""; show("登录"); }
            else { request(() -> session.client().call("POST", "/api/rooms/" + id + "/join", null, Room.class),
                    room -> { session.room(room.id()); show("在线大厅"); }); }
        });
        join.setDisabled(!joinable); body.add(card).growX().padBottom(8).row();
    }
    private void room(Room room) {
        section("我的房间", gameName(room.gameId())); Table box = card("platform-current-room");
        line(box, PlayerText.roomState(room.status()) + " · " + room.members().size() + " / 2 人", true);
        for (Member member : room.members()) {
            line(box, "玩家 " + (member.slot() + 1) + " · " + member.displayName()
                    + (member.userId().equals(session.profile().userId()) ? "（你）" : "") + " · "
                    + (member.ready() ? "已准备" : "未准备") + " · " + (member.online() ? "在线" : "已离开"), false);
        }
        line(box, room.status().equals("ALLOCATED") ? "双方已准备，请点击进入比赛。" : "两位玩家都准备后，即可进入比赛。", false);
        Table actions = new Table();
        boolean ready = room.members().stream().filter(m -> m.userId().equals(session.profile().userId())).anyMatch(Member::ready);
        if (room.status().equals("OPEN")) {
            button(actions, ready ? "取消准备" : "准备", "platform-ready", () -> request(() -> session.client().call("POST", "/api/rooms/" + room.id() + "/ready", new Ready(!ready), Room.class), r -> show("在线大厅")), true);
        }
        if (room.status().equals("ALLOCATED")) {
            button(actions, "进入比赛", "platform-play", () -> request(() -> session.client().call("POST", "/api/rooms/" + room.id() + "/ticket", null, Ticket.class), app::platformPlay), true);
        }
        button(actions, "退出房间", "platform-leave", () -> request(() -> { session.client().call("POST", "/api/rooms/" + room.id() + "/leave", null, Void.class); return true; }, v -> { session.room(""); show("在线大厅"); }));
        box.add(actions).left(); body.add(box).growX().row();
    }

    private record SocialData(Friend[] friends, Invitation[] invitations) { }
    private void friends() {
        section("好友与邀请", "通过用户名找到朋友"); Table add = card("platform-friend-form");
        TextField username = field("platform-friend-name", "", 20); username.setMessageText("输入朋友的用户名");
        add.add(username).growX().minWidth(0).height(40);
        button(add, "添加好友", "platform-add-friend", () -> {
            String name = username.getText();
            if (name.isBlank()) { feedback("请输入朋友的用户名"); return; }
            request(() -> { session.client().call("POST", "/api/friends", new FriendRequest(name), Void.class); return true; }, v -> show("好友"));
        }, true); body.add(add).growX().row();
        request(() -> new SocialData(session.client().call("GET", "/api/friends", null, Friend[].class), session.client().call("GET", "/api/invitations", null, Invitation[].class)), data -> {
            if (data.friends().length == 0 && data.invitations().length == 0) { empty("platform-empty-friends", "一起玩的朋友，会让这里更热闹", "输入朋友的用户名发送请求；加入房间后，即可邀请好友同玩。"); }
            for (Friend friend : data.friends()) {
                Table card = card("platform-friend-" + friend.userId()); Table text = new Table();
                line(text, friend.displayName(), true);
                line(text, friend.username() + " · " + (friend.status().equals("ACCEPTED") ? "好友" : friend.incoming() ? "请求添加你为好友" : "等待对方接受") + " · " + (friend.online() ? "在线" : "离线"), false);
                card.add(text).growX().minWidth(0);
                if (friend.status().equals("PENDING") && friend.incoming()) { button(card, "接受", "friend-accept-" + friend.userId(), () -> request(() -> { session.client().call("POST", "/api/friends/" + friend.userId() + "/accept", null, Void.class); return true; }, v -> show("好友"))); }
                if (friend.status().equals("ACCEPTED") && !session.room().isEmpty()) { button(card, "邀请同玩", "friend-invite-" + friend.userId(), () -> request(() -> { session.client().call("POST", "/api/rooms/" + session.room() + "/invite", new Invite(friend.userId()), Void.class); return true; }, v -> { show("好友"); feedback("邀请已发送"); })); }
                button(card, "移除", "friend-remove-" + friend.userId(), () -> request(() -> { session.client().call("DELETE", "/api/friends/" + friend.userId(), null, Void.class); return true; }, v -> show("好友")));
                body.add(card).growX().padTop(8).row();
            }
            for (Invitation invitation : data.invitations()) {
                Table card = card("platform-invitation-" + invitation.roomId());
                // fromUser 是账户 ID，接口没有提供昵称时不向玩家显示内部编号。
                card.add(copy("收到一份 " + gameName(invitation.gameId()) + " 同玩邀请", true)).growX().minWidth(0);
                button(card, "接受邀请", "invite-" + invitation.roomId(), () -> request(() -> session.client().call("POST", "/api/rooms/" + invitation.roomId() + "/join", null, Room.class), room -> { session.room(room.id()); show("在线大厅"); }));
                body.add(card).growX().padTop(8).row();
            }
            feedback("好友列表已更新");
        }, "正在查找好友与邀请…");
    }
    private record HistoryData(Match[] matches, Rank[] ranks) { }
    private void history() { rank("tank"); }
    private void rank(String game) {
        request(() -> new HistoryData(session.client().call("GET", "/api/history?game=" + game, null, Match[].class), session.client().call("GET", "/api/ranks/" + game, null, Rank[].class)), data -> {
            clear(); Table navigation = new Table();
            for (String value : List.of("tank", "snake", "fight")) {
                navigation.add(bind(theme.nav(gameName(value), value.equals(game)), "platform-rank-" + value, () -> rank(value))).height(36).spaceRight(4);
            }
            body.add(navigation).left().row(); section("排行榜", gameName(game));
            if (data.ranks().length == 0) { empty("platform-empty-ranks", "榜单等待新的名字", "完成在线比赛后，在这里查看积分与排名。"); }
            int position = 0;
            for (Rank rank : data.ranks()) {
                Table card = card("platform-ranking-" + (++position));
                // 名次独立成列；长昵称单行省略，不再把名次挤到上一行。
                card.add(theme.label(Integer.toString(position), theme.assets.heading, position <= 3 ? ThemeManager.GOLD : ThemeManager.MUTED)).width(36).left();
                Label name = theme.label(rank.displayName(), theme.assets.body, ThemeManager.TEXT); name.setEllipsis(true);
                card.add(name).growX().minWidth(0).left();
                card.add(theme.caption(rank.wins() + " 胜 · " + PlayerText.winRate(rank.winRate()) + " 胜率 · " + rank.points() + " 积分")).padLeft(12);
                body.add(card).growX().padBottom(6).row();
            }
            section("最近比赛", "最多显示 50 场");
            if (data.matches().length == 0) { empty("platform-empty-history", "还没有比赛记录", "去在线大厅完成一场对决，留下你的第一份战绩。"); }
            for (Match match : data.matches()) {
                String result = match.participants().stream().filter(p -> p.userId().equals(session.profile().userId()))
                        .map(p -> PlayerText.result(p.result()) + " · " + PlayerText.points(p.points())).findFirst().orElse("等待结果");
                Table card = card("platform-history-card-" + match.id());
                Label entry = copy(gameName(match.gameId()) + " · " + PlayerText.roomState(match.status()) + " · " + result, false);
                entry.setName("platform-history-match-" + match.id()); card.add(entry).growX().minWidth(0);
                body.add(card).growX().padBottom(6).row();
            }
            feedback("战绩已更新 · " + gameName(game));
        }, "正在加载排行榜与比赛记录…");
    }

    @Override public void render(float delta) {
        for (int i = 0; i < 32; i++) { Runnable action = events.poll(); if (action == null) { break; } if (!leaving) { action.run(); } }
        if (!leaving && !busy && tab.equals("在线大厅") && System.nanoTime() > nextRefresh && !scroll.isDragging() && !scroll.isPanning()) { lobby(true); }
        super.render(delta);
    }
    @Override public void hide() { leaving = true; events.clear(); worker.shutdownNow(); super.hide(); }
    @Override public void dispose() { if (isDisposed()) { return; } hide(); super.dispose(); }
}
