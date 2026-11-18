package dev.gamecore.server.deployment;

import com.sun.net.httpserver.HttpServer;
import dev.gamecore.server.multigame.NettyMultiGameServer;
import dev.gamecore.server.devtools.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** 仅字面回环的只读健康/指标端点；单线程有界请求队列，指标一秒采样，不暴露调试控制或密钥。 */
public final class HealthEndpoint implements AutoCloseable {
    private final HttpServer http;
    private final ThreadPoolExecutor requests=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->new Thread(r,"game-health"),new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"game-metrics"));
    private volatile byte[] metrics="{}".getBytes(StandardCharsets.UTF_8);
    private volatile boolean draining;
    public HealthEndpoint(NettyMultiGameServer game,int port) throws IOException {
        http=HttpServer.create(new InetSocketAddress("127.0.0.1",port),8);http.setExecutor(requests);
        var telemetry=new ServerTelemetry(game);
        http.createContext("/",exchange->{
            try{String path=exchange.getRequestURI().getRawPath();boolean health=path.equals("/health");int code=!exchange.getRequestMethod().equals("GET")?405:health?(game.healthy()&&!draining?200:503):path.equals("/metrics")?200:404;
                byte[] body=health?(code==200?"{\"status\":\"UP\"}":"{\"status\":\"DOWN\"}").getBytes(StandardCharsets.UTF_8):code==200?metrics:"{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(code,body.length);exchange.getResponseBody().write(body);
            }finally{exchange.close();}
        });
        sampler.scheduleWithFixedDelay(()->{try{metrics=Json.encode(telemetry.sample()).getBytes(StandardCharsets.UTF_8);}catch(RuntimeException failure){System.getLogger("gamecore.deployment").log(System.Logger.Level.WARNING,"METRICS_SAMPLE_FAILED");}},0,1,TimeUnit.SECONDS);
        http.start();
    }
    public int port(){return http.getAddress().getPort();}
    public void drain(){draining=true;}
    @Override public void close(){draining=true;http.stop(0);sampler.shutdownNow();requests.shutdownNow();try{sampler.awaitTermination(3,TimeUnit.SECONDS);requests.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
