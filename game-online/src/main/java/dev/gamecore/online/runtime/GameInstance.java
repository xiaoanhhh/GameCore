package dev.gamecore.online.runtime;
import dev.gamecore.api.online.*;
import dev.gamecore.api.session.*;
import dev.gamecore.online.ProtocolValues;
import java.util.List;
/** 类型擦除只在适配边界；内部仍是同一I/S，Room无需知道Tank/Snake/Fight。绑定首次创建线程。 */
public interface GameInstance extends AutoCloseable{
    GameStep initial();
    void start();
    void pause();
    void resume();
    void validateInput(byte[] command);
    byte[] held(byte[] command);
    byte[] neutral();
    GameStep step(List<AppliedInput> inputs);
    @Override void close();
    static <I,S> GameInstance create(GameFactory<I,S> factory,GameRuleConfig config,long seed){
        factory.validate(config);var session=factory.sessions().create(config,seed);var owner=Thread.currentThread();
        return new GameInstance(){
            private boolean closed;
            private void check(){if(closed||Thread.currentThread()!=owner){throw new IllegalStateException("Game owner violated");}}
            private GameStep frame(List<AppliedInput> inputs,long nanos){
                long start=System.nanoTime();var snapshot=session.snapshot();byte[] bytes=factory.snapshots().encode(snapshot);
                return new GameStep(session.tickNumber(),session.state(),factory.snapshots().outcome(snapshot),bytes,factory.snapshots().entityCount(snapshot),session.events().stream().map(ProtocolValues::event).toList(),inputs,nanos,System.nanoTime()-start);
            }
            @Override public GameStep initial(){check();return frame(List.of(),0);}
            @Override public void start(){check();session.start();}
            @Override public void pause(){check();session.pause();}
            @Override public void resume(){check();session.resume();}
            @Override public void validateInput(byte[] value){check();factory.inputs().decode(value);}
            @Override public byte[] held(byte[] value){check();return factory.inputs().held(factory.inputs().decode(value)).map(factory.inputs()::encode).orElse(null);}
            @Override public byte[] neutral(){check();return factory.inputs().neutral().map(factory.inputs()::encode).orElse(null);}
            @Override public GameStep step(List<AppliedInput> values){
                check();long next=session.tickNumber()+1;
                for(var v:values){if(!session.submitInput(new PlayerInput<>(next,v.player(),v.canonicalSequence(),factory.inputs().decode(v.command())))){throw new IllegalArgumentException("Canonical input rejected");}}
                long start=System.nanoTime();session.tick();long nanos=System.nanoTime()-start;
                if(session.tickNumber()!=next){throw new IllegalStateException("Core did not advance");}return frame(values,nanos);
            }
            @Override public void close(){check();closed=true;if(session instanceof AutoCloseable resource){try{resource.close();}catch(Exception e){throw new IllegalStateException(e);}}}
        };
    }
}
