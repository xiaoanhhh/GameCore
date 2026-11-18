package dev.gamecore.api.session;

/** 平台生命周期；各游戏内部仍可保留自己的规则状态。结束后只能创建新会话。 */
public enum SessionState { READY, RUNNING, PAUSED, FINISHED }
