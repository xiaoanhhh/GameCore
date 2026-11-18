package dev.gamecore.client.platform;

import dev.gamecore.platform.api.PlatformHttpClient;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 发布端提供固定平台入口；普通玩家无需填地址。未配置/错误配置均不猜测公网IP或降级HTTPS。 */
public record PlatformEndpoint(String url,String problem) {
    public static PlatformEndpoint load(){
        var bundled=new Properties();
        try(var input=PlatformEndpoint.class.getResourceAsStream("/gamecore-platform.properties")){
            if(input!=null){byte[] bytes=input.readNBytes(4097);if(bytes.length>4096){return new PlatformEndpoint("","平台发布配置无效");}bundled.load(new StringReader(new String(bytes,StandardCharsets.UTF_8)));}
            var system=new HashMap<String,String>();String override=System.getProperty("gamecore.platform.url");if(override!=null){system.put("gamecore.platform.url",override);}
            return resolve(system,System.getenv(),bundled);
        }catch(IOException|RuntimeException failure){return new PlatformEndpoint("","平台发布配置无效，本地游戏仍可使用");}
    }
    /** 纯值入口便于验证优先级，空的显式覆盖可清空发布地址；不创建HTTP客户端。 */
    public static PlatformEndpoint resolve(Map<String,String> system,Map<String,String> environment,Properties bundled){
        String value=system.containsKey("gamecore.platform.url")?system.get("gamecore.platform.url"):
            environment.getOrDefault("GAMECORE_PLATFORM_URL",bundled.getProperty("platform.url",""));
        value=value==null?"":value.strip();if(value.isEmpty()){return new PlatformEndpoint("","");}
        try{if(value.length()>180){throw new IllegalArgumentException();}return new PlatformEndpoint(PlatformHttpClient.endpoint(value).toString(),"");}
        catch(IllegalArgumentException invalid){return new PlatformEndpoint("","平台发布配置无效，请联系发布者；本地游戏仍可使用");}
    }
    public boolean configured(){return !url.isEmpty()&&problem.isEmpty();}
}
