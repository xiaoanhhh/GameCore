package dev.gamecore.platform.api;

import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;

/** 有超时/响应容量上限的同步HTTP端口；调用者必须使用有界后台执行器，不能在UI/IO/Tick等待。 */
public final class PlatformHttpClient implements AutoCloseable {
    public static final JsonMapper JSON = JsonMapper.builder().build();
    private final URI base;
    private final HttpClient http;
    private volatile String token = "";
    private volatile ExchangeObserver observer;
    /**
     * 诊断观察端口：只有方法、路径、状态、错误码和耗时，接触不到请求头、请求体和响应体。
     * 在发起请求的线程上回调，实现不能阻塞；status 为 0 表示没有收到 HTTP 响应。
     */
    public interface ExchangeObserver { void exchange(String method, String path, int status, String code, long nanos); }
    public PlatformHttpClient(String url) {
        base = endpoint(url);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    /** 纯值校验供发布配置复用；无效地址不会先创建HTTP线程/连接资源。 */
    public static URI endpoint(String url) {
        URI base = URI.create(Objects.requireNonNull(url));
        if (base.getHost()==null || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null
                || base.getPort()==0 || base.getPort()>65535
                || !(base.getPath().isEmpty()||base.getPath().equals("/"))
                || !("https".equals(base.getScheme()) || "http".equals(base.getScheme())
                    && java.util.Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(base.getHost()))) {
            throw new IllegalArgumentException("平台地址须为HTTPS；HTTP只允许回环开发地址");
        }
        return base;
    }
    public void token(String value) { token=Objects.requireNonNull(value); }
    public void observer(ExchangeObserver value) { observer=value; }
    public <T> T call(String method, String path, Object body, Class<T> type) {
        return call(method,path,body,type,false);
    }
    public <T> T internal(String path, Object body, Class<T> type) {
        return call("POST",path,body,type,true);
    }
    private <T> T call(String method,String path,Object body,Class<T> type,boolean internal) {
        var watcher=observer;
        if(watcher==null){return exchange(method,path,body,type,internal,new int[1]);}
        int[] status=new int[1];long started=System.nanoTime();
        try{
            T value=exchange(method,path,body,type,internal,status);
            report(watcher,method,path,status[0],"",started);
            return value;
        }catch(Failure failure){report(watcher,method,path,failure.status(),failure.getMessage(),started);throw failure;}
        catch(RuntimeException failure){report(watcher,method,path,status[0],"INVALID_RESPONSE",started);throw failure;}
    }
    private static void report(ExchangeObserver watcher,String method,String path,int status,String code,long started){
        try{watcher.exchange(method,path,status,code,System.nanoTime()-started);}catch(RuntimeException ignored){ /* 诊断失败不改变请求结果。 */ }
    }
    private <T> T exchange(String method,String path,Object body,Class<T> type,boolean internal,int[] status) {
        if (!path.startsWith("/api/") || path.contains("..") || path.length()>256) { throw new IllegalArgumentException("API path"); }
        try {
            String auth=token;
            var builder=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(4))
                    .header("Accept","application/json").header("Content-Type","application/json");
            if(!auth.isEmpty()){builder.header(internal?"X-Game-Server-Key":"Authorization",internal?auth:"Bearer "+auth);}
            var request=builder.method(method, body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
            var pending=http.sendAsync(request,ignored->new LimitedBody());
            final HttpResponse<byte[]> response;
            try{response=pending.get(4,java.util.concurrent.TimeUnit.SECONDS);}
            catch(InterruptedException e){pending.cancel(true);throw e;}
            catch(java.util.concurrent.TimeoutException e){pending.cancel(true);throw new Failure(504,"PLATFORM_TIMEOUT");}
            catch(java.util.concurrent.ExecutionException e){throw new Failure(503,"PLATFORM_UNAVAILABLE");}
            status[0]=response.statusCode();
            byte[] bytes=response.body();
            if(bytes.length>262144){throw new Failure(502,"RESPONSE_LIMIT");}
            if(response.statusCode()/100!=2){
                String code="HTTP_"+response.statusCode();
                try{code=JSON.readValue(bytes,PlatformMessages.ApiError.class).code();}catch(RuntimeException ignored){ }
                throw new Failure(response.statusCode(),code);
            }
            return type==Void.class?null:JSON.readValue(bytes,type);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new Failure(503,"INTERRUPTED");}
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> body=new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();private java.util.concurrent.Flow.Subscription subscription;
        @Override public java.util.concurrent.CompletionStage<byte[]> getBody(){return body;}
        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription s){subscription=s;s.request(1);}
        @Override public void onNext(java.util.List<java.nio.ByteBuffer> buffers){
            for(var buffer:buffers){if(bytes.size()+buffer.remaining()>262144){subscription.cancel();body.completeExceptionally(new IOException("Response capacity"));return;}byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}subscription.request(1);
        }
        @Override public void onError(Throwable error){body.completeExceptionally(error);}
        @Override public void onComplete(){body.complete(bytes.toByteArray());}
    }
    /** 只携带稳定错误码，避免在UI或日志泄露请求体/凭证。 */
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID=1L;
        private final int status;
        public Failure(int status,String code){super(code);this.status=status;}
        public int status(){return status;}
    }
    @Override public void close(){http.close();token="";}
}
