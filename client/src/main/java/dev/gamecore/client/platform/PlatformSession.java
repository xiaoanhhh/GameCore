package dev.gamecore.client.platform;
import dev.gamecore.platform.api.*;
import dev.gamecore.platform.api.PlatformMessages.*;
import java.util.Objects;
/** 仅内存账户状态；密码/令牌不写设置文件。HTTP只由页面后台工作者调用，离线页面不依赖它。 */
public final class PlatformSession {
    private final PlatformEndpoint endpoint;
    private volatile PlatformHttpClient client;private volatile Profile profile;private volatile String url="",room="";
    private volatile PlatformHttpClient.ExchangeObserver observer;
    private boolean closed;
    private final java.util.concurrent.CompletableFuture<Void> terminated=new java.util.concurrent.CompletableFuture<>();
    public PlatformSession(){this(PlatformEndpoint.load());}
    public PlatformSession(PlatformEndpoint endpoint){this.endpoint=Objects.requireNonNull(endpoint);}
    /** 仅供后台开发/测试重配；正常UI没有地址输入。错误地址不会破坏当前连接。 */
    public synchronized void configure(String target){if(closed){throw new IllegalStateException("CLOSED");}PlatformHttpClient.endpoint(target);if(!target.equals(url)||client==null){var next=new PlatformHttpClient(target);next.observer(observer);if(client!=null){client.close();}client=next;url=target;profile=null;room="";}}
    /** 诊断观察端口跨越 configure 重建 HTTP 客户端后仍然生效；观察方自行决定是否记录。 */
    public synchronized void observer(PlatformHttpClient.ExchangeObserver value){observer=value;if(client!=null){client.observer(value);}}
    public Profile login(String username,String password,boolean register){var c=client();var body=new Credentials(username,password);if(register){c.call("POST","/api/auth/register",body,Profile.class);}var value=c.call("POST","/api/auth/login",body,Login.class);
        synchronized(this){if(closed||client!=c){throw new IllegalStateException("CLOSED");}if(value.profile()==null||value.token()==null||value.token().isBlank()){throw new IllegalArgumentException("Invalid login response");}c.token(value.token());profile=value.profile();return profile;}}
    public void logout(){try{client().call("POST","/api/auth/logout",null,Void.class);}finally{expire();}}
    /** HTTP只能由页面的有界工作者访问，构造/启动离线客户端不会创建网络线程。 */
    public synchronized PlatformHttpClient client(){if(closed){throw new IllegalStateException("CLOSED");}if(client==null){if(!endpoint.configured()){throw new IllegalStateException("PLATFORM_NOT_CONFIGURED");}configure(endpoint.url());}return client;}
    public LobbyOverview browse(){return client().call("GET","/api/public/lobby",null,LobbyOverview.class);}
    public synchronized void expire(){if(client!=null){client.token("");}profile=null;room="";}
    public Profile profile(){return profile;}public void profile(Profile value){profile=value;}
    public String url(){return url.isEmpty()?endpoint.url():url;}
    public boolean configured(){return !url().isEmpty();}
    /** 默认启动游客大厅；显式离线启动直接进入原本地首页，不要求登录或平台可用。 */
    public boolean autoOpen(){return !Boolean.parseBoolean(System.getProperty("gamecore.offline",System.getenv().getOrDefault("GAMECORE_OFFLINE","false")));}
    public String configurationNotice(){return endpoint.problem().isEmpty()?"此安装包暂时无法使用在线服务，请联系发布者。本地游戏仍可使用。":endpoint.problem();}
    public String room(){return room;}public void room(String value){room=value;}
    public java.util.concurrent.CompletableFuture<Void> termination(){return terminated;}
    public synchronized void closeAsync(){if(closed){return;}closed=true;var current=client;client=null;profile=null;if(current==null){terminated.complete(null);}else{var thread=new Thread(()->{try{current.close();terminated.complete(null);}catch(RuntimeException failure){terminated.completeExceptionally(failure);}},"platform-client-close");thread.setDaemon(true);thread.start();}}
}
