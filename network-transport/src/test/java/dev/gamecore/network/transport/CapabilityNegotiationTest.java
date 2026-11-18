package dev.gamecore.network.transport;

import dev.gamecore.protocol.GameProtocol;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 旧服务器不能静默降级完整在线功能；拒绝后保留首个原因并释放客户端 IO。 */
class CapabilityNegotiationTest {
    @Test void interactiveClientRequiresExplicitServerCapability() throws Exception{
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());var executor=Executors.newSingleThreadExecutor()){
            var peer=executor.submit(()->{try(var socket=server.accept();var input=new DataInputStream(socket.getInputStream());var output=new DataOutputStream(socket.getOutputStream())){
                int size=input.readInt();assertTrue(size>0&&size<65536);assertTrue(GameProtocol.Envelope.parseFrom(input.readNBytes(size)).hasHello());
                byte[] welcome=GameProtocol.Envelope.newBuilder().setProtocolVersion(2).setWelcome(GameProtocol.Welcome.newBuilder().setCredential("legacy").setConnectionId("legacy")).build().toByteArray();
                output.writeInt(welcome.length);output.write(welcome);output.flush();assertEquals(-1,input.read());
            }catch(IOException failure){throw new UncheckedIOException(failure);}});
            var client=GameNetworkConnection.openTicket("127.0.0.1",server.getLocalPort(),"fight-1","fight","1","fight/7","",null,false,null,true);
            try{assertThrows(ExecutionException.class,()->client.ready().get(4,TimeUnit.SECONDS));assertEquals("SERVER_UPGRADE_REQUIRED",client.failure());}
            finally{client.closeAsync().get(5,TimeUnit.SECONDS);}peer.get(4,TimeUnit.SECONDS);
        }
    }
}
