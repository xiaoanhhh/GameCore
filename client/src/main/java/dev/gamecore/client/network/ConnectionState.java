package dev.gamecore.client.network;

/** 连接与对局展示状态；暂停输入不等于暂停服务器。终态仍允许页面返回或重新连接。 */
public enum ConnectionState {
    NEW, CONNECTING, NEGOTIATING, JOINING, WAITING, RUNNING, FINISHING, FINISHED,
    DISCONNECTED, REJECTED, INCOMPATIBLE, FAILED, CLOSED;
    public boolean terminal() {
        return switch (this) {
            case FINISHED, DISCONNECTED, REJECTED, INCOMPATIBLE, FAILED, CLOSED -> true;
            default -> false;
        };
    }
}
