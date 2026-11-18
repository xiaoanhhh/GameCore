package dev.gamecore.platform;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.online.runtime.*;
import dev.gamecore.platform.api.*;
import dev.gamecore.platform.api.PlatformMessages.*;
import dev.gamecore.protocol.GameProtocol;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 平台并发竞争：同时注册、抢座、准备、兑换票据、提交结果，以及多房间完整组局。
 * 使用真实 PostgreSQL 和 HTTP；每轮独立 schema，只接受本机 *_test 库。 */
@Tag("postgres")
class PlatformConcurrencyTest {
    static String jdbc,user,password,schema,key,url;static ConfigurableApplicationContext context;
    static final String PASSWORD="Example_Only_4931";
    static final Map<String,Object> report=new ConcurrentSkipListMap<>();
    record Account(PlatformHttpClient http,Login login) implements AutoCloseable {UUID id(){return UUID.fromString(login.profile().userId());}@Override public void close(){http.close();}}

    @BeforeAll static void start() throws Exception{
        jdbc=System.getenv("GAMECORE_TEST_DB_URL");user=System.getenv("GAMECORE_TEST_DB_USER");password=System.getenv("GAMECORE_TEST_DB_PASSWORD");
        assertNotNull(jdbc,"Set GAMECORE_TEST_DB_URL for postgresTest");assertTrue(jdbc.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[a-z0-9_]*_test"),"Only local *_test database accepted");
        schema="concurrency_"+UUID.randomUUID().toString().replace("-","");key=Tokens.create();
        try(var connection=DriverManager.getConnection(jdbc,user,password);var sql=connection.createStatement()){sql.execute("CREATE SCHEMA "+schema);}
        var app=new SpringApplication(PlatformApplication.class);
        app.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-override",Map.of("platform.auth-limit",1000,"server.port",0,"spring.datasource.url",jdbc+"?currentSchema="+schema,"spring.datasource.username",user,"spring.datasource.password",password,"platform.server-key",key,"spring.main.banner-mode","off"))));
        context=app.run();url="http://127.0.0.1:"+context.getEnvironment().getRequiredProperty("local.server.port");
    }
    @AfterAll static void stop() throws Exception{
        if(context!=null){context.close();}
        if(schema!=null&&schema.matches("concurrency_[a-f0-9]{32}")){try(var c=DriverManager.getConnection(jdbc,user,password);var sql=c.createStatement()){sql.execute("DROP SCHEMA "+schema+" CASCADE");}}
        var path=Path.of(System.getProperty("gamecore.concurrency.report","build/reports/concurrency/platform.json")).toAbsolutePath();Files.createDirectories(path.getParent());
        Files.writeString(path,PlatformHttpClient.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
    static JdbcTemplate db(){return context.getBean(JdbcTemplate.class);}
    static Account account(){var c=new PlatformHttpClient(url);String name="u_"+UUID.randomUUID().toString().replace("-","").substring(0,12);c.call("POST","/api/auth/register",new Credentials(name,PASSWORD),Profile.class);var login=c.call("POST","/api/auth/login",new Credentials(name,PASSWORD),Login.class);c.token(login.token());return new Account(c,login);}
    /** 所有任务在同一闸门后同时开始；Failure 按状态码计入结果，其他异常直接让测试失败。 */
    static <T> List<Object> race(int threads,List<Callable<T>> actions) throws Exception{
        try(var pool=Executors.newFixedThreadPool(threads)){
            var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<Object>>();
            for(var action:actions){tasks.add(pool.submit(()->{gate.await();try{return (Object)action.call();}catch(PlatformHttpClient.Failure failure){return failure.status();}}));}
            gate.countDown();var results=new ArrayList<Object>();for(var task:tasks){results.add(task.get(30,TimeUnit.SECONDS));}return results;
        }
    }
    static long count(List<Object> results,Class<?> type){return results.stream().filter(type::isInstance).count();}
    static Set<Object> failures(List<Object> results){var set=new HashSet<Object>(results);set.removeIf(v->!(v instanceof Integer));return set;}

    @Test void sameUsernameRegisteredConcurrentlyCreatesOneAccount() throws Exception{
        String name="same_"+UUID.randomUUID().toString().replace("-","").substring(0,10);
        var clients=new ArrayList<PlatformHttpClient>();for(int i=0;i<8;i++){clients.add(new PlatformHttpClient(url));}
        try{
            var results=race(8,clients.stream().<Callable<Object>>map(c->()->c.call("POST","/api/auth/register",new Credentials(name,PASSWORD),Profile.class)).toList());
            assertEquals(1,count(results,Profile.class));assertEquals(Set.of(409),failures(results));
            assertEquals(1,db().queryForObject("SELECT count(*) FROM users WHERE username=?",Integer.class,name.toLowerCase(Locale.ROOT)));
        }finally{clients.forEach(PlatformHttpClient::close);}
    }

    @Test void eightPlayersRacingForLastSeatLeaveOneWinner() throws Exception{
        var joiners=new ArrayList<Account>();for(int i=0;i<8;i++){joiners.add(account());}
        try(var owner=account()){
            Room room=owner.http.call("POST","/api/rooms",new CreateRoom("snake"),Room.class);
            var results=race(8,joiners.stream().<Callable<Object>>map(a->()->a.http.call("POST","/api/rooms/"+room.id()+"/join",null,Room.class)).toList());
            assertEquals(1,count(results,Room.class));assertEquals(Set.of(409),failures(results));
            assertEquals(2,db().queryForObject("SELECT count(*) FROM room_members WHERE room_id=?",Integer.class,room.id()));
            assertEquals(2,db().queryForObject("SELECT count(DISTINCT slot) FROM room_members WHERE room_id=?",Integer.class,room.id()));
        }finally{joiners.forEach(Account::close);}
    }

    @Test void simultaneousReadyAllocatesExactlyOneMatchPerRoom() throws Exception{
        for(int round=0;round<6;round++){
            try(var a=account();var b=account()){
                Room room=a.http.call("POST","/api/rooms",new CreateRoom("tank"),Room.class);b.http.call("POST","/api/rooms/"+room.id()+"/join",null,Room.class);
                var results=race(2,List.<Callable<Object>>of(()->a.http.call("POST","/api/rooms/"+room.id()+"/ready",new Ready(true),Room.class),()->b.http.call("POST","/api/rooms/"+room.id()+"/ready",new Ready(true),Room.class)));
                assertEquals(2,count(results,Room.class));
                assertEquals(1,db().queryForObject("SELECT count(*) FROM match_records WHERE room_id=?",Integer.class,room.id()));
                assertEquals("ALLOCATED",a.http.call("GET","/api/rooms/"+room.id(),null,Room.class).status());
                a.http.call("POST","/api/rooms/"+room.id()+"/leave",null,Void.class);
            }
        }
    }

    static Room allocate(Account a,Account b,String game){Room r=a.http.call("POST","/api/rooms",new CreateRoom(game),Room.class);b.http.call("POST","/api/rooms/"+r.id()+"/join",null,Room.class);a.http.call("POST","/api/rooms/"+r.id()+"/ready",new Ready(true),Room.class);return b.http.call("POST","/api/rooms/"+r.id()+"/ready",new Ready(true),Room.class);}

    @Test void oneTicketRedeemedConcurrentlyAdmitsOnlyOneConnection() throws Exception{
        try(var a=account();var b=account();var internal=new PlatformHttpClient(url)){
            internal.token(key);Room room=allocate(a,b,"snake");Ticket ticket=a.http.call("POST","/api/rooms/"+room.id()+"/ticket",null,Ticket.class);
            var actions=new ArrayList<Callable<Object>>();for(int i=0;i<8;i++){String connection="connection-"+i;actions.add(()->internal.internal("/api/internal/tickets/redeem",new Redeem(ticket.credential(),connection),Admission.class));}
            var results=race(8,actions);assertEquals(1,count(results,Admission.class));assertEquals(Set.of(401),failures(results));
            a.http.call("POST","/api/rooms/"+room.id()+"/leave",null,Void.class);
        }
    }

    @Test void conflictingResultsSubmittedConcurrentlySettleOnlyOnce() throws Exception{
        try(var a=account();var b=account();var internal=new PlatformHttpClient(url)){
            internal.token(key);Room room=allocate(a,b,"snake");Admission admission=null;
            for(Account who:List.of(a,b)){Ticket t=who.http.call("POST","/api/rooms/"+room.id()+"/ticket",null,Ticket.class);admission=internal.internal("/api/internal/tickets/redeem",new Redeem(t.credential(),UUID.randomUUID().toString()),Admission.class);}
            var factory=GameRegistry.builtIn().require("snake");GameStep last;
            try(var game=GameInstance.create(factory,factory.defaults(),admission.seed())){game.start();last=game.initial();for(int i=0;i<2000&&!last.state().name().equals("FINISHED");i++){last=game.step(i==0?List.of(new AppliedInput(1,1,1,GameProtocol.GameInput.newBuilder().setSnake(GameProtocol.SnakeInput.newBuilder().setDirection(GameProtocol.SnakeDirection.UP)).build().toByteArray())):List.of());}}
            var snapshot=GameProtocol.Snapshot.newBuilder().setRoomId(room.id()).setMatchId(room.matchId()).setGameId("snake").setStatus("FINISHED").setSimulationTick(last.tick()).setState(ByteString.copyFrom(last.snapshot())).setStateHash(ByteString.copyFrom(StateHashes.hash("snake",factory.type().rulesVersion(),last.snapshot()))).build();
            String encoded=Base64.getEncoder().encodeToString(snapshot.toByteArray());
            var rules=new Finish(room.matchId(),"RULES",encoded);var closed=new Finish(room.matchId(),"ROOM_CLOSED",encoded);
            var actions=new ArrayList<Callable<Object>>();for(int i=0;i<12;i++){var body=i%2==0?rules:closed;actions.add(()->internal.internal("/api/internal/results",body,Match.class));}
            var results=race(12,actions);
            var settled=results.stream().filter(Match.class::isInstance).distinct().toList();assertEquals(1,settled.size(),"成功返回的结算结果必须相同");
            assertTrue(Set.of(409).containsAll(failures(results)),"只允许冲突被拒绝："+failures(results));
            assertEquals(2,db().queryForObject("SELECT count(*) FROM match_participants WHERE match_id=?",Integer.class,UUID.fromString(room.matchId())));
            var mine=Arrays.stream(a.http.call("GET","/api/history",null,Match[].class)).filter(m->m.id().equals(room.matchId())).toList();assertEquals(1,mine.size());
            var rank=Arrays.stream(a.http.call("GET","/api/ranks/snake",null,Rank[].class)).filter(r->r.userId().equals(a.id().toString())).toList();assertTrue(rank.size()<=1);if(!rank.isEmpty()){assertEquals(1,rank.getFirst().games());}
            report.put("conflictingResults",Map.of("submitted",12,"accepted",count(results,Match.class),"rejected409",12-count(results,Match.class),"winningStatus",((Match)settled.getFirst()).status()));
        }
    }

    private static void timed(List<Long> samples,Runnable call){long start=System.nanoTime();call.run();samples.add(System.nanoTime()-start);}
    private static double percentile(List<Long> sorted,double p){return sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(p*sorted.size())-1))/1e6;}

    /** 20 对玩家同时走完建房、加入、双方准备、领票；记录真实 HTTP 耗时，只描述本机回环。 */
    @Test void twentyPairsCompleteLobbyFlowConcurrently() throws Exception{
        int pairs=20;var accounts=new ArrayList<Account>();for(int i=0;i<pairs*2;i++){accounts.add(account());}
        var samples=Collections.synchronizedList(new ArrayList<Long>());var failed=new ConcurrentLinkedQueue<String>();var completed=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(pairs)){
            var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<?>>();long start=System.nanoTime();
            for(int i=0;i<pairs;i++){
                var a=accounts.get(i*2);var b=accounts.get(i*2+1);
                tasks.add(pool.submit(()->{try{gate.await();
                    var room=new Room[1];
                    timed(samples,()->room[0]=a.http.call("POST","/api/rooms",new CreateRoom("tank"),Room.class));String path="/api/rooms/"+room[0].id();
                    timed(samples,()->b.http.call("POST",path+"/join",null,Room.class));
                    timed(samples,()->a.http.call("POST",path+"/ready",new Ready(true),Room.class));
                    timed(samples,()->b.http.call("POST",path+"/ready",new Ready(true),Room.class));
                    timed(samples,()->a.http.call("POST",path+"/ticket",null,Ticket.class));
                    timed(samples,()->b.http.call("POST",path+"/ticket",null,Ticket.class));
                    completed.incrementAndGet();
                }catch(Exception failure){failed.add(failure.toString());}}));
            }
            gate.countDown();for(var task:tasks){task.get(60,TimeUnit.SECONDS);}
            double seconds=(System.nanoTime()-start)/1e9;
            assertEquals(List.of(),List.copyOf(failed));assertEquals(pairs,completed.get());
            var ids=accounts.stream().map(Account::id).toArray();var marks=String.join(",",Collections.nCopies(ids.length,"?"));
            assertEquals(pairs,db().queryForObject("SELECT count(DISTINCT match_id) FROM match_participants WHERE user_id IN ("+marks+")",Integer.class,ids));
            var sorted=new ArrayList<>(samples);Collections.sort(sorted);
            report.put("lobbyFlow",new TreeMap<>(Map.of("pairs",pairs,"requests",sorted.size(),"seconds",seconds,"requestsPerSecond",sorted.size()/seconds,"p50Ms",percentile(sorted,.5),"p95Ms",percentile(sorted,.95),"maxMs",sorted.getLast()/1e6)));
        }finally{accounts.forEach(Account::close);}
    }
}
