package dev.gamecore.client.ui;

import java.util.Locale;

/** 玩家可见的状态翻译；只投影显示值，不参与权限、房间可加入性或比赛判定。 */
public final class PlayerText {
    private PlayerText() { }

    public static String roomState(String state) {
        return switch (state) {
            case "OPEN" -> "等待准备";
            case "ALLOCATED" -> "比赛就绪";
            case "RUNNING" -> "正在比赛";
            case "FINISHED" -> "已结束";
            case "ABORTED", "FAILED" -> "已中止";
            case "CLOSED" -> "已关闭";
            default -> "状态已更新";
        };
    }

    public static String result(String result) {
        return switch (result) {
            case "WIN" -> "胜利";
            case "LOSS" -> "落败";
            case "DRAW" -> "平局";
            case "ABORTED", "NONE" -> "本局中止";
            case "PLAYER_ONE" -> "玩家 1 获胜";
            case "PLAYER_TWO" -> "玩家 2 获胜";
            default -> "等待结果";
        };
    }

    public static String points(int points) {
        return (points > 0 ? "+" : "") + points + " 积分";
    }

    public static String winRate(double rate) {
        return String.format(Locale.ROOT, "%.0f%%", rate * 100);
    }

    public static String error(String code) {
        return switch (code) {
            case "PLATFORM_UNAVAILABLE", "PLATFORM_TIMEOUT", "DATABASE_UNAVAILABLE" -> "平台暂时无法连接，请重试或稍后再来。本地游戏仍可使用。";
            case "LOGIN_FAILED", "UNAUTHORIZED" -> "用户名或密码错误，或登录已过期，请重新登录";
            case "CONFLICT" -> "信息或房间状态已改变，请刷新后重试";
            case "PASSWORD_LENGTH" -> "密码至少 10 个字符；中文或符号较多时，请缩短至 72 字节以内";
            case "INVALID_USERNAME" -> "用户名需为 3–20 位字母、数字或下划线";
            case "INVALID_NAME" -> "昵称不能为空，最多 32 个字符";
            case "LOBBY_RATE_LIMIT", "AUTH_RATE_LIMIT" -> "操作过于频繁，请稍后重试";
            case "ALREADY_IN_ROOM" -> "你已在房间中，请先退出当前房间";
            case "NOT_READY" -> "两位玩家都准备后，即可进入比赛";
            case "TICKET_ALREADY_USED", "TICKET_EXPIRED" -> "入场信息已失效，请返回大厅重新进入比赛";
            case "ROOM_UNAVAILABLE", "ROOM_FULL" -> "房间已满或已开始，请刷新后选择其他房间";
            case "VERSION_MISMATCH", "GAME_MISMATCH", "MAP_MISMATCH" -> "游戏版本不一致，请更新客户端后重试";
            case "FRIEND_NOT_FOUND", "USER_NOT_FOUND", "NOT_FOUND" -> "未找到相关玩家或房间，请检查后重试";
            case "FORBIDDEN" -> "暂时无法执行此操作，请检查当前房间状态";
            case "CONTROL_REJECTED","STALE_CONTROL" -> "房间选择或状态已改变，请确认后重新操作";
            case "RESULT_PENDING" -> "上一局结果正在保存，请稍候再点击重新开始";
            case "REMATCH_UNAVAILABLE" -> "暂时无法开始新一局，请稍后重试或返回大厅";
            case "SESSION_CONTROLS_REQUIRED","SERVER_UPGRADE_REQUIRED" -> "服务器或对方客户端需要升级后才能使用完整在线功能";
            default -> "暂时无法完成操作，请刷新后重试；也可以返回本地游戏";
        };
    }
}
