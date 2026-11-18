package dev.gamecore.client.traffic;

import com.badlogic.gdx.graphics.Color;

/** 交互记录的类型与识别色；面板的图例和过滤按钮按声明顺序排列。 */
public enum TrafficKind {
    CONNECTION("连接", "4aa8ff"),
    HEARTBEAT("心跳", "9aa5b8"),
    INPUT("输入", "37d6a0"),
    SNAPSHOT("快照", "b794f6"),
    ROOM("房间", "f6c945"),
    EVENT("事件", "ff9f43"),
    PLATFORM("平台", "5ad1e6"),
    ERROR("错误", "ff6b6b");

    public final String label;
    public final Color color;
    TrafficKind(String label, String rgb) { this.label = label; color = Color.valueOf(rgb); }
}
