package dev.gamecore.client.network;

import io.netty.util.NetUtil;

/** 地址只是连接目标，不接受URL/凭证；DNS交给Netty异步解析器，不在渲染线程解析。 */
public record ServerEndpoint(String host, int port) {
    /** 调试注入不通过DNS推断可信环境；只接受明确的回环字面量/localhost。 */
    public boolean loopback() { return host.equals("127.0.0.1") || host.equals("::1") || host.equalsIgnoreCase("localhost"); }
    public ServerEndpoint {
        if (host == null) { throw new IllegalArgumentException("服务器地址不能为空"); }
        host = host.strip();
        if (host.startsWith("[") && host.endsWith("]")) { host = host.substring(1, host.length() - 1); }
        boolean ip = NetUtil.isValidIpV4Address(host) || NetUtil.isValidIpV6Address(host);
        boolean dns = host.length() <= 253 && host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")
                && !host.matches("[0-9.]+")
                && java.util.Arrays.stream(host.split("\\.", -1)).allMatch(label -> label.length() <= 63
                    && label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"));
        if (!ip && !dns) { throw new IllegalArgumentException("请输入服务器IP或主机名，不含http和路径"); }
        if (port < 1 || port > 65535) { throw new IllegalArgumentException("端口范围为1到65535"); }
    }
}
