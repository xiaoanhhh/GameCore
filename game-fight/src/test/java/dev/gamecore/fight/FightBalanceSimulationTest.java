package dev.gamecore.fight;

import dev.gamecore.api.session.PlayerInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 固定种子、角色/控制器交换的两百局实测；不把臆想的胜率/时长当作保证或为过线歪调 Bot。 */
class FightBalanceSimulationTest {
    @Test void twoHundredSeededMatchesProduceMeasuredReport() throws Exception {
        double[] seconds=new double[200];int one=0,two=0,draw=0,damage=0,ultimate=0,attacks=0,hits=0,naiwa=0,feidudu=0;
        for(int match=0;match<200;match++){
            boolean swap=match%2==1;var s=new FightSession(new FightSessionFactory.Config(swap?FighterKind.FEIDUDU:FighterKind.NAIWA,swap?FighterKind.NAIWA:FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),match);
            var a=new FightBot(match/2*101L+7);var b=new FightBot(match/2*173L+91);s.start();long last=0;
            while(!s.isFinished()){
                var v=s.snapshot();s.submitInput(new PlayerInput<>(s.tickNumber()+1,0,0,(swap?b:a).command(v,0)));s.submitInput(new PlayerInput<>(s.tickNumber()+1,1,1,(swap?a:b).command(v,1)));s.tick();
                for(var hit:s.snapshot().impacts()){if(hit.id()>last){damage+=hit.damage();if(hit.ultimate()){ultimate+=hit.damage();}last=hit.id();}}
                assertTrue(s.tickNumber()<=FightSession.MATCH_TICKS);
            }
            var v=s.snapshot();seconds[match]=v.tick()/60.0;
            switch(v.result()){case PLAYER_ONE->one++;case PLAYER_TWO->two++;case DRAW->draw++;case NONE->fail("Unsettled match");}
            if(v.result()!=FightSnapshot.Result.DRAW){var winner=v.fighters().get(v.result()==FightSnapshot.Result.PLAYER_ONE?0:1).kind();if(winner==FighterKind.NAIWA){naiwa++;}else{feidudu++;}}
            for(var f:v.fighters()){attacks+=f.attacks();hits+=f.hits();}
        }
        Arrays.sort(seconds);double median=(seconds[99]+seconds[100])/2;
        Path report=Path.of("build/reports/fight-balance/summary.json");Files.createDirectories(report.getParent());
        String json=String.format(java.util.Locale.ROOT,"{\"matches\":200,\"medianSeconds\":%.3f,\"p1Wins\":%d,\"p2Wins\":%d,\"naiwaWins\":%d,\"feiduduWins\":%d,\"draws\":%d,\"ultimateDamageShare\":%.5f,\"attackHitRate\":%.5f,\"totalDamage\":%d,\"scope\":\"seeded bots, not human balance validation\"}%n",median,one,two,naiwa,feidudu,draw,damage==0?0:(double)ultimate/damage,attacks==0?0:(double)hits/attacks,damage);
        Files.writeString(report,json);System.out.println("BALANCE_REPORT "+json);assertEquals(200,one+two+draw);assertTrue(damage>0);
    }
}
