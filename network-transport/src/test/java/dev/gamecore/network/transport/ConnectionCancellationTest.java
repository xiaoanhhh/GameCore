package dev.gamecore.network.transport;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
/** 页面在握手/注册尚未完成时退出，也必须终止自身IO组，不能依赖收到服务器响应。 */
class ConnectionCancellationTest {
    @Test void immediateCancellationTerminatesBeforeHandshake() throws Exception{
        try(var passive=new ServerSocket(0,32,InetAddress.getLoopbackAddress())){
            for(int i=0;i<20;i++){
                try(var connection=GameNetworkConnection.open("127.0.0.1",passive.getLocalPort(),"tank-1","tank","1","tank/1")){
                    connection.closeAsync().get(5,TimeUnit.SECONDS);assertTrue(connection.termination().isDone());assertFalse(connection.active());
                }
            }
        }
    }
}
