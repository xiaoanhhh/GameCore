package dev.gamecore.online;
import dev.gamecore.api.online.*;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.online.runtime.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** 对在线注册表中的每款游戏执行同一组平台契约检查；新增游戏登记后自动纳入，无需复制测试。 */
class RegisteredGameContractTest {
    static Stream<GameFactory<?,?>> registered(){return GameRegistry.builtIn().all().stream();}

    @ParameterizedTest @MethodSource("registered")
    void typeAndDefaultsSatisfyPlatformBounds(GameFactory<?,?> factory){
        var type=factory.type();
        assertTrue(type.id().matches("[a-z][a-z0-9-]{0,31}"));
        assertTrue(type.minPlayers()>=1&&type.maxPlayers()<=8&&type.minPlayers()<=type.maxPlayers());
        assertEquals(0,type.simulationTps()%type.defaultRoomTps(),"规则频率须能被房间频率整除");
        assertEquals(factory.getClass(),GameRegistry.builtIn().require(type.id()).getClass());
        assertDoesNotThrow(()->factory.validate(factory.defaults()));
    }

    @ParameterizedTest @MethodSource("registered")
    void sameSeedAndBotInputsStayDeterministicAndSnapshotsRoundTrip(GameFactory<?,?> factory){
        assertDeterministic(factory);
    }

    @ParameterizedTest @MethodSource("registered")
    void inputCodecRejectsEveryOtherGamePayloadAndGarbage(GameFactory<?,?> factory){
        for(var other:GameRegistry.builtIn().all()){
            if(other.type().id().equals(factory.type().id())){continue;}
            byte[] foreign=botCommand(other);
            assertThrows(IllegalArgumentException.class,()->factory.inputs().decode(foreign),
                    factory.type().id()+" 不应接受 "+other.type().id()+" 的输入");
        }
        assertThrows(IllegalArgumentException.class,()->factory.inputs().decode(new byte[]{(byte)0xFF,(byte)0xFF,0x01}));
        factory.inputs().neutral().ifPresent(n->assertDoesNotThrow(()->roundTripInput(factory)));
    }

    private static <I,S> byte[] botCommand(GameFactory<I,S> f){
        var session=f.sessions().create(f.defaults(),7);session.start();
        return f.inputs().encode(f.bot(session.snapshot(),0,0,true));
    }
    private static <I,S> void roundTripInput(GameFactory<I,S> f){
        I neutral=f.inputs().neutral().orElseThrow();
        assertEquals(neutral,f.inputs().decode(f.inputs().encode(neutral)));
    }
    /** 两个实例使用同一种子，并由测试 Bot 产生同样的合法意图，逐步比较快照字节与状态哈希。 */
    private static <I,S> void assertDeterministic(GameFactory<I,S> f){
        var config=f.defaults();
        try(var a=GameInstance.create(f,config,2026);var b=GameInstance.create(f,config,2026)){
            assertArrayEquals(a.initial().snapshot(),b.initial().snapshot());
            a.start();b.start();
            S view=f.snapshots().decode(a.initial().snapshot());long sequence=0;
            for(int step=0;step<240;step++){
                var inputs=new ArrayList<AppliedInput>();
                for(int slot=0;slot<config.players();slot++){
                    I command=f.bot(view,slot,step,true);
                    if(command!=null){sequence++;inputs.add(new AppliedInput(slot,sequence,sequence,f.inputs().encode(command)));}
                }
                var x=a.step(inputs);var y=b.step(inputs);
                assertArrayEquals(x.snapshot(),y.snapshot(),f.type().id()+" 第 "+step+" 步出现分歧");
                assertArrayEquals(StateHashes.hash(f.type().id(),f.type().rulesVersion(),x.snapshot()),
                        StateHashes.hash(f.type().id(),f.type().rulesVersion(),y.snapshot()));
                view=f.snapshots().decode(x.snapshot());
                assertArrayEquals(x.snapshot(),f.snapshots().encode(view),"快照编码往返应保持字节一致");
                if(x.state()==SessionState.FINISHED){assertFalse(f.snapshots().outcome(view).isBlank());break;}
            }
        }
    }
}
