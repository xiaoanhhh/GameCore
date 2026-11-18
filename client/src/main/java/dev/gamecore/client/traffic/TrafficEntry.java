package dev.gamecore.client.traffic;

/** 一行不可变记录，可跨线程交给渲染线程。text 已脱敏和截断。 */
public record TrafficEntry(String time, TrafficKind kind, TrafficDirection direction, String text) { }
