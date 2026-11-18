package dev.gamecore.network.transport;

import com.google.protobuf.MessageLite;

/**
 * 连接消息的只读观察端口，只在开发诊断时安装。
 * 回调运行在连接的 EventLoop 上，实现只能做有界内存操作，不能阻塞，也不能修改或转发消息。
 * 消息里有握手凭证和平台票据，实现只能取明确需要的字段，不能调用 toString 或原样保存。
 */
public interface TrafficObserver {
    /** 消息即将写入连接。 */
    void sent(MessageLite message);
    /** 消息已完成解码，业务处理器尚未处理。 */
    void received(MessageLite message);
    /** 连接状态变化；state 是可直接展示的短文本。 */
    void connection(String state);
}
