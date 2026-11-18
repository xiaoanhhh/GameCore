package dev.gamecore.client.traffic;

/** 记录相对本机的方向。平台 HTTP 一次请求响应合成一行，用“往返”表示。 */
public enum TrafficDirection {
    SENT("发"), RECEIVED("收"), ROUND_TRIP("往返"), LOCAL("本地");

    public final String label;
    TrafficDirection(String label) { this.label = label; }
}
