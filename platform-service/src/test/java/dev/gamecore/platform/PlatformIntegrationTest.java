package dev.gamecore.platform;
import dev.gamecore.platform.api.*;
import dev.gamecore.platform.api.PlatformMessages.*;
import dev.gamecore.online.*;
import dev.gamecore.online.runtime.*;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.network.transport.GameNetworkConnection;
import dev.gamecore.server.multigame.*;
import dev.gamecore.server.platform.*;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
/** 真实PostgreSQL/Flyway/HTTP/Security/TCP；每轮独立schema，严禁指向非测试库。 */
@Tag("postgres") @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformIntegrationTest {
    static String jdbc,user,password,schema,key,url;static int port;static ConfigurableApplicationContext context;
    @TempDir Path temp;
    static final String PASSWORD="Example_Only_4931";
    record Account(PlatformHttpClient http,Login login) implements AutoCloseable {UUID id(){return UUID.fromString(login.profile().userId());}@Override public void close(){http.close();}}
    @BeforeAll static void start() throws Exception {
        jdbc=System.getenv("GAMECORE_TEST_DB_URL");user=System.getenv("GAMECORE_TEST_DB_USER");password=System.getenv("GAMECORE_TEST_DB_PASSWORD");
        assertNotNull(jdbc,"Set GAMECORE_TEST_DB_URL for postgresTest");assertTrue(jdbc.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[a-z0-9_]*_test"),"Only local *_test database accepted");
        schema="stage09_"+UUID.randomUUID().toString().replace("-","");key=Tokens.create();
        try(var connection=DriverManager.getConnection(jdbc,user,password);var sql=connection.createStatement()){sql.execute("CREATE SCHEMA "+schema);}
        boot(0);
    }
    static void boot(int requested){var app=new SpringApplication(PlatformApplication.class);
        app.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-override",Map.of("platform.auth-limit",1000,"server.port",requested,"spring.datasource.url",jdbc+"?currentSchema="+schema,"spring.datasource.username",user,"spring.datasource.password",password,"platform.server-key",key,"spring.main.banner-mode","off"))));
        context=app.run();port=Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));url="http://127.0.0.1:"+port;
    }
    @AfterAll static void stop() throws Exception {if(context!=null){context.close();}if(schema!=null&&schema.matches("stage09_[a-f0-9]{32}")){try(var c=DriverManager.getConnection(jdbc,user,password);var sql=c.createStatement()){sql.execute("DROP SCHEMA "+schema+" CASCADE");}}}
    static JdbcTemplate db(){return context.getBean(JdbcTemplate.class);}
    static Account account(){var c=new PlatformHttpClient(url);String name="u_"+UUID.randomUUID().toString().replace("-","").substring(0,12);c.call("POST","/api/auth/register",new Credentials(name,PASSWORD),Profile.class);var login=c.call("POST","/api/auth/login",new Credentials(name,PASSWORD),Login.class);c.token(login.token());return new Account(c,login);}
    static void failure(int status,Runnable action){var e=assertThrows(PlatformHttpClient.Failure.class,action::run);assertEquals(status,e.status(),e.getMessage());}
    static void await(BooleanSupplier done) throws Exception {long end=System.nanoTime()+12_000_000_000L;while(!done.getAsBoolean()&&System.nanoTime()<end){new CountDownLatch(1).await(20,TimeUnit.MILLISECONDS);}assertTrue(done.getAsBoolean(),"Timed out waiting for real IO");}
    static Room allocate(Account a,Account b,String game){Room r=a.http.call("POST","/api/rooms",new CreateRoom(game),Room.class);b.http.call("POST","/api/rooms/"+r.id()+"/join",null,Room.class);a.http.call("POST","/api/rooms/"+r.id()+"/ready",new Ready(true),Room.class);return b.http.call("POST","/api/rooms/"+r.id()+"/ready",new Ready(true),Room.class);}
    static void control(GameNetworkConnection c,java.util.concurrent.atomic.AtomicLong sequence,GameProtocol.RoomAction action,Map<String,String> choices){c.control(sequence.incrementAndGet(),action,choices);}
    @Test @Order(60) void interactiveChoicesRestartAndNextRoundResultsUseSeparateMatches() throws Exception {
        var games=GameRegistry.builtIn();
        try(var a=account();var b=account();var internal=new PlatformHttpClient(url);
            var gateway=new PlatformGateway(url,key,temp.resolve("interactive-outbox"),games);
            var server=NettyMultiGameServer.startPlatform("127.0.0.1",0,games,true,gateway,s->gateway.observer())){
            internal.token(key);var room=allocate(a,b,"fight");
            var ta=a.http.call("POST","/api/rooms/"+room.id()+"/ticket",null,Ticket.class);var tb=b.http.call("POST","/api/rooms/"+room.id()+"/ticket",null,Ticket.class);
            try(var ca=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),room.id(),"fight","1","fight/7",ta.credential(),null,false,null,true);
                var cb=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),room.id(),"fight","1","fight/7",tb.credential(),null,false,null,true)){
                ca.ready().get(8,TimeUnit.SECONDS);cb.ready().get(8,TimeUnit.SECONDS);await(()->ca.roomState()!=null&&ca.roomState().getPhase().equals("SETUP"));
                var sa=new java.util.concurrent.atomic.AtomicLong();var sb=new java.util.concurrent.atomic.AtomicLong();String map=dev.gamecore.fight.FightMap.values()[3].name();
                control(ca,sa,GameProtocol.RoomAction.SELECT,Map.of("one","FEIDUDU","map",map));control(cb,sb,GameProtocol.RoomAction.SELECT,Map.of("two","NAIWA"));
                await(()->dev.gamecore.online.ProtocolValues.config(ca.roomState().getConfig()).options().equals(Map.of("one","FEIDUDU","two","NAIWA","map",map))&&ca.roomState().getRevision()==cb.roomState().getRevision());
                control(ca,sa,GameProtocol.RoomAction.READY,Map.of());control(cb,sb,GameProtocol.RoomAction.READY,Map.of());await(()->ca.roomState().getPhase().equals("OPENING"));
                assertEquals(0,server.rooms().require(room.id()).metrics().coreTick());control(ca,sa,GameProtocol.RoomAction.SKIP_OPENING,Map.of());control(cb,sb,GameProtocol.RoomAction.SKIP_OPENING,Map.of());
                await(()->ca.roomState().getPhase().equals("RUNNING")&&cb.roomState().getPhase().equals("RUNNING"));String original=room.matchId();
                control(ca,sa,GameProtocol.RoomAction.RESTART,Map.of());control(cb,sb,GameProtocol.RoomAction.RESTART,Map.of());
                await(()->!ca.joined().getMatchId().equals(original)||ca.roomState().getPhase().equals("ABORTED"));
                await(()->db().queryForObject("SELECT status FROM match_records WHERE id=?",String.class,UUID.fromString(original)).equals("ABORTED"));
                if(ca.joined().getMatchId().equals(original)){
                    control(ca,sa,GameProtocol.RoomAction.RESTART,Map.of());control(cb,sb,GameProtocol.RoomAction.RESTART,Map.of());
                }
                await(()->{
                    if(!ca.failure().isEmpty()||!cb.failure().isEmpty()){
                        var diagnostic=new ArrayList<String>();GameProtocol.Envelope event;
                        while((event=ca.poll())!=null){diagnostic.add(event.hasError()?event.getError().getCode():event.hasResult()?event.getResult().getReason():event.getPayloadCase().name());}
                        fail("Connection lost: "+ca.failure()+"/"+cb.failure()+" room="+server.rooms().rooms().stream().map(r->r.metrics().status()).toList()+" phase="+(ca.roomState()==null?"null":ca.roomState().getPhase())+" newIdentity="+!ca.joined().getMatchId().equals(original)+" events="+diagnostic);
                    }
                    return !ca.joined().getMatchId().equals(original)&&ca.joined().getMatchId().equals(cb.joined().getMatchId())&&ca.roomState()!=null&&ca.roomState().getPhase().equals("SETUP");
                });
                String next=ca.joined().getMatchId();assertEquals(next,internal.internal("/api/internal/matches/"+original+"/next",null,NextMatch.class).matchId());
                assertEquals(2,db().queryForObject("SELECT count(*) FROM join_tickets WHERE match_id=? AND consumed",Integer.class,UUID.fromString(next)));
                control(ca,sa,GameProtocol.RoomAction.READY,Map.of());control(cb,sb,GameProtocol.RoomAction.READY,Map.of());await(()->ca.roomState().getPhase().equals("OPENING")&&cb.roomState().getPhase().equals("OPENING"));
                control(ca,sa,GameProtocol.RoomAction.SKIP_OPENING,Map.of());control(cb,sb,GameProtocol.RoomAction.SKIP_OPENING,Map.of());await(()->ca.roomState().getPhase().equals("RUNNING"));
                for(int i=0;i<101&&!server.rooms().require(room.id()).metrics().status().equals("FINISHED");i++){server.rooms().require(room.id()).debug(true,60).get(3,TimeUnit.SECONDS);}
                await(()->db().queryForObject("SELECT status FROM match_records WHERE id=?",String.class,UUID.fromString(next)).equals("FINISHED"));
                assertEquals(2,db().queryForObject("SELECT count(*) FROM match_records WHERE room_id=?",Integer.class,room.id()));
                assertEquals(2,db().queryForObject("SELECT count(*) FROM match_participants WHERE match_id=? AND result='DRAW' AND points=1",Integer.class,UUID.fromString(next)));
                assertEquals(0,db().queryForObject("SELECT sum(points)::int FROM match_participants WHERE match_id=?",Integer.class,UUID.fromString(original)));
                assertEquals("RESTARTED",db().queryForObject("SELECT reason FROM match_records WHERE id=?",String.class,UUID.fromString(original)));
                assertEquals(2,server.rooms().require(room.id()).metrics().players());
            }
        }
    }
    @Test @Order(1) void registrationLoginHashAndUniqueName(){try(var a=account();var guest=new PlatformHttpClient(url)){
        String hash=db().queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,a.id());assertTrue(hash.startsWith("{bcrypt}"));assertFalse(hash.contains(PASSWORD));assertEquals(a.id().toString(),a.http.call("GET","/api/me",null,Profile.class).userId());
        failure(409,()->guest.call("POST","/api/auth/register",new Credentials(a.login.profile().username().toUpperCase(Locale.ROOT),PASSWORD),Profile.class));
        failure(400,()->guest.call("POST","/api/auth/register",new Credentials("short","123"),Profile.class));
        failure(401,()->guest.call("POST","/api/auth/login",new Credentials(a.login.profile().username(),"wrong_password_123"),Login.class));
    }}
    @Test @Order(2) void unauthorizedAndServerAuthorityAreSeparated(){try(var a=account();var guest=new PlatformHttpClient(url)){
        failure(401,()->guest.call("GET","/api/rooms",null,Room[].class));failure(403,()->a.http.call("POST","/api/internal/results",new Finish(UUID.randomUUID().toString(),"RULES",""),Match.class));
        guest.token("incorrect-service-key");failure(401,()->guest.internal("/api/internal/tickets/redeem",new Redeem(Tokens.create(),"fake"),Admission.class));
        failure(400,()->a.http.call("POST","/api/rooms",Map.of("gameId","tank","playerId","victim"),Room.class));
    }}
    @Test @Order(3) void logoutExpiryAndDisabledAccount(){try(var a=account()){
        db().update("UPDATE login_sessions SET expires_at=now()-interval '1 second' WHERE user_id=?",a.id());failure(401,()->a.http.call("GET","/api/me",null,Profile.class));
    }try(var a=account()){
        a.http.call("POST","/api/auth/logout",null,Void.class);failure(401,()->a.http.call("GET","/api/me",null,Profile.class));
    }try(var a=account()){
        db().update("UPDATE users SET disabled=true WHERE id=?",a.id());failure(401,()->a.http.call("GET","/api/me",null,Profile.class));
    }}
    @Test @Order(4) void friendsInvitationsReadyAndLeave(){try(var a=account();var b=account()){
        a.http.call("POST","/api/friends",new FriendRequest(b.login.profile().username()),Void.class);a.http.call("POST","/api/friends",new FriendRequest(b.login.profile().username()),Void.class);
        assertEquals(1,b.http.call("GET","/api/friends",null,Friend[].class).length);failure(409,()->a.http.call("POST","/api/friends/"+b.id()+"/accept",null,Void.class));
        b.http.call("POST","/api/friends/"+a.id()+"/accept",null,Void.class);Room r=a.http.call("POST","/api/rooms",new CreateRoom("tank"),Room.class);
        a.http.call("POST","/api/rooms/"+r.id()+"/invite",new Invite(b.id().toString()),Void.class);assertEquals(r.id(),b.http.call("GET","/api/invitations",null,Invitation[].class)[0].roomId());
        Room joined=b.http.call("POST","/api/rooms/"+r.id()+"/join",null,Room.class);assertEquals(2,joined.members().size());assertTrue(joined.members().stream().allMatch(Member::online));
        a.http.call("POST","/api/rooms/"+r.id()+"/ready",new Ready(true),Room.class);b.http.call("POST","/api/rooms/"+r.id()+"/leave",null,Void.class);
        assertFalse(a.http.call("GET","/api/rooms/"+r.id(),null,Room.class).members().getFirst().ready());a.http.call("POST","/api/rooms/"+r.id()+"/leave",null,Void.class);
    }}
    @Test @Order(5) void concurrentCreateAndDuplicateJoinAreBounded() throws Exception {try(var a=account();var b=account()){
        try(var workers=Executors.newFixedThreadPool(6)){var tasks=new ArrayList<Future<Room>>();for(int i=0;i<6;i++){tasks.add(workers.submit(()->{try{return a.http.call("POST","/api/rooms",new CreateRoom("snake"),Room.class);}catch(PlatformHttpClient.Failure e){assertEquals(409,e.status());return null;}}));}
            var successes=new ArrayList<Room>();for(var t:tasks){var r=t.get(8,TimeUnit.SECONDS);if(r!=null){successes.add(r);}}assertEquals(1,successes.size());Room r=successes.getFirst();
            var joins=new ArrayList<Future<Room>>();for(int i=0;i<6;i++){joins.add(workers.submit(()->b.http.call("POST","/api/rooms/"+r.id()+"/join",null,Room.class)));}for(var t:joins){assertEquals(2,t.get().members().size());}
            assertEquals(2,db().queryForObject("SELECT count(*) FROM room_members WHERE room_id=?",Integer.class,r.id()));a.http.call("POST","/api/rooms/"+r.id()+"/leave",null,Void.class);b.http.call("POST","/api/rooms/"+r.id()+"/leave",null,Void.class);
        }
    }}
    @Test @Order(6) void ticketExpiresAndCannotBeConsumedTwice(){try(var a=account();var b=account();var internal=new PlatformHttpClient(url)){
        internal.token(key);Room r=allocate(a,b,"snake");Ticket ta=a.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class);
        final Ticket expired=ta;db().update("UPDATE join_tickets SET expires_at=now()-interval '1 second' WHERE user_id=?",a.id());failure(401,()->internal.internal("/api/internal/tickets/redeem",new Redeem(expired.credential(),"x"),Admission.class));
        ta=a.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class);final Ticket good=ta;Admission admission=internal.internal("/api/internal/tickets/redeem",new Redeem(good.credential(),"server-assigned-1"),Admission.class);
        assertEquals(a.id().toString(),admission.userId());assertEquals(0,admission.slot());failure(401,()->internal.internal("/api/internal/tickets/redeem",new Redeem(good.credential(),"server-assigned-2"),Admission.class));
        a.http.call("POST","/api/rooms/"+r.id()+"/leave",null,Void.class);
    }}
    @Test @Order(6) void logoutRevokesOutstandingJoinTicket(){try(var a=account();var b=account();var internal=new PlatformHttpClient(url)){
        internal.token(key);Room r=allocate(a,b,"snake");Ticket ticket=a.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class);a.http.call("POST","/api/auth/logout",null,Void.class);
        failure(401,()->internal.internal("/api/internal/tickets/redeem",new Redeem(ticket.credential(),"new-connection"),Admission.class));b.http.call("POST","/api/rooms/"+r.id()+"/leave",null,Void.class);
    }}
    @Test @Order(7) void authoritativeResultIsIdempotentAndClientCannotChangeScore() throws Exception {try(var a=account();var b=account();var internal=new PlatformHttpClient(url)){
        internal.token(key);Room r=allocate(a,b,"snake");Admission admission=null;for(Account who:List.of(a,b)){Ticket t=who.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class);admission=internal.internal("/api/internal/tickets/redeem",new Redeem(t.credential(),UUID.randomUUID().toString()),Admission.class);}
        var factory=GameRegistry.builtIn().require("snake");GameStep last;try(var game=GameInstance.create(factory,factory.defaults(),admission.seed())){game.start();last=game.initial();for(int i=0;i<2000&&!last.state().name().equals("FINISHED");i++){last=game.step(i==0?List.of(new AppliedInput(1,1,1,GameProtocol.GameInput.newBuilder().setSnake(GameProtocol.SnakeInput.newBuilder().setDirection(GameProtocol.SnakeDirection.UP)).build().toByteArray())):List.of());}}assertEquals("FINISHED",last.state().name());assertEquals("PLAYER_ONE",last.outcome());
        var snap=GameProtocol.Snapshot.newBuilder().setRoomId(r.id()).setMatchId(r.matchId()).setGameId("snake").setStatus("FINISHED").setSimulationTick(last.tick()).setState(ByteString.copyFrom(last.snapshot())).setStateHash(ByteString.copyFrom(StateHashes.hash("snake",factory.type().rulesVersion(),last.snapshot()))).build();
        Finish finish=new Finish(r.matchId(),"RULES",Base64.getEncoder().encodeToString(snap.toByteArray()));Match first=internal.internal("/api/internal/results",finish,Match.class);Match again=internal.internal("/api/internal/results",finish,Match.class);assertEquals(first,again);assertEquals("FINISHED",first.status());
        try(var workers=Executors.newFixedThreadPool(5)){var retries=new ArrayList<Future<Match>>();for(int i=0;i<5;i++){retries.add(workers.submit(()->internal.internal("/api/internal/results",finish,Match.class)));}for(var retry:retries){assertEquals(first,retry.get(6,TimeUnit.SECONDS));}}
        Rank winner=Arrays.stream(a.http.call("GET","/api/ranks/snake",null,Rank[].class)).filter(rank->rank.userId().equals(a.id().toString())).findFirst().orElseThrow();
        Rank loser=Arrays.stream(a.http.call("GET","/api/ranks/snake",null,Rank[].class)).filter(rank->rank.userId().equals(b.id().toString())).findFirst().orElseThrow();
        assertEquals(1,winner.wins());assertEquals(1,winner.games());assertEquals(1.0,winner.winRate());assertEquals(3,winner.points());assertEquals(0,loser.wins());assertEquals(1,loser.games());assertEquals(0.0,loser.winRate());assertEquals(0,loser.points());
        long points=a.http.call("GET","/api/ranks/snake",null,Rank[].class).length;assertTrue(points>=2);assertEquals(1,a.http.call("GET","/api/history",null,Match[].class).length);assertEquals(0,a.http.call("GET","/api/history?game=tank",null,Match[].class).length);assertEquals(1,a.http.call("GET","/api/history?game=snake",null,Match[].class).length);
        failure(409,()->internal.internal("/api/internal/results",new Finish(r.matchId(),"ROOM_CLOSED",finish.snapshot()),Match.class));
        failure(403,()->a.http.call("POST","/api/internal/results",finish,Match.class));assertEquals(2,db().queryForObject("SELECT count(*) FROM match_participants WHERE match_id=?",Integer.class,UUID.fromString(r.matchId())));
    }}
    @ParameterizedTest @ValueSource(strings={"tank","snake","fight"}) @Order(8) void realNettyHandoffUsesAssignedSlotAndUploadsRuleResult(String game) throws Exception {try(var a=account();var b=account();var gateway=new PlatformGateway(url,key,temp.resolve("outbox"),GameRegistry.builtIn());var server=NettyMultiGameServer.startPlatform("127.0.0.1",0,GameRegistry.builtIn(),true,gateway,s->gateway.observer())){
        Room r=allocate(a,b,game);Ticket ta=a.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class),tb=b.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class);
        try(var p2=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),r.id(),game,"1",GameRegistry.builtIn().require(game).type().rulesVersion(),tb.credential(),null);var p1=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),r.id(),game,"1",GameRegistry.builtIn().require(game).type().rulesVersion(),ta.credential(),null)){
            assertEquals(1,p2.ready().get(6,TimeUnit.SECONDS).getPlayerSlot());assertEquals(0,p1.ready().get(6,TimeUnit.SECONDS).getPlayerSlot());assertEquals(r.matchId(),p1.joined().getMatchId());
            await(()->p1.latest()!=null&&p1.latest().getStatus().equals("RUNNING"));if(game.equals("snake")){p1.input(1,p1.latest().getServerTick()+2,GameProtocol.GameInput.newBuilder().setSnake(GameProtocol.SnakeInput.newBuilder().setDirection(GameProtocol.SnakeDirection.RIGHT)).build().toByteArray());}
            RoomActor room=server.rooms().require(r.id());for(int i=0;i<200&&room.metrics().status().equals("RUNNING");i++){room.debug(true,60).get(3,TimeUnit.SECONDS);}await(()->gateway.delivered()==1);
            Match result=a.http.call("GET","/api/matches/"+r.matchId(),null,Match.class);assertEquals("FINISHED",result.status());assertTrue(result.tick()>0);assertEquals(2,result.participants().size());assertEquals(0,gateway.queued());
        }
    }}
    @Test @Order(9) void platformEndpointHasNoAnonymousBypass() throws Exception {try(var gateway=new PlatformGateway(url,key,temp.resolve("auth-outbox"),GameRegistry.builtIn());var server=NettyMultiGameServer.startPlatform("127.0.0.1",0,GameRegistry.builtIn(),false,gateway,s->gateway.observer());var anonymous=GameNetworkConnection.open("127.0.0.1",server.address().getPort(),"snake-1","snake","1","snake/1")){
        assertThrows(ExecutionException.class,()->anonymous.ready().get(6,TimeUnit.SECONDS));assertTrue(server.rooms().rooms().isEmpty());
    }}
    @Test @Order(9) void disconnectProducesAbortedUnscoredMatch() throws Exception {try(var a=account();var b=account();var gateway=new PlatformGateway(url,key,temp.resolve("disconnect-outbox"),GameRegistry.builtIn());var server=NettyMultiGameServer.startPlatform("127.0.0.1",0,GameRegistry.builtIn(),true,gateway,x->gateway.observer())){
        Room r=allocate(a,b,"tank");Ticket ta=a.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class),tb=b.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class);
        try(var p1=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),r.id(),"tank","1","tank/1",ta.credential(),null);var p2=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),r.id(),"tank","1","tank/1",tb.credential(),null)){
            p1.ready().get(6,TimeUnit.SECONDS);p2.ready().get(6,TimeUnit.SECONDS);await(()->p2.latest()!=null&&p2.latest().getStatus().equals("RUNNING"));p1.closeAsync().get(5,TimeUnit.SECONDS);await(()->gateway.delivered()==1);
            Match match=a.http.call("GET","/api/matches/"+r.matchId(),null,Match.class);assertEquals("ABORTED",match.status());assertTrue(match.participants().stream().allMatch(p->p.points()==0&&p.result().equals("ABORTED")));assertTrue(server.rooms().rooms().isEmpty());
        }
    }}
    @Test @Order(9) void waitingDisconnectReleasesPlatformReservationImmediately() throws Exception {try(var a=account();var b=account();var gateway=new PlatformGateway(url,key,temp.resolve("waiting-outbox"),GameRegistry.builtIn());var server=NettyMultiGameServer.startPlatform("127.0.0.1",0,GameRegistry.builtIn(),false,gateway,x->PlatformServerMain.combine(new RoomActor.Observer(){},gateway.observer()))){
        Room room=allocate(a,b,"snake");Ticket ticket=a.http.call("POST","/api/rooms/"+room.id()+"/ticket",null,Ticket.class);
        try(var p=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),room.id(),"snake","1","snake/1",ticket.credential(),null)){p.ready().get(6,TimeUnit.SECONDS);p.closeAsync().get(5,TimeUnit.SECONDS);await(()->gateway.delivered()==1);}
        Match result=a.http.call("GET","/api/matches/"+room.matchId(),null,Match.class);assertEquals("ABORTED",result.status());assertTrue(result.participants().stream().allMatch(p->p.points()==0));assertTrue(server.rooms().rooms().isEmpty());
    }}
    @Test @Order(10) void restartRetainsSessionAndOutboxRetriesAfterPlatformOutage() throws Exception {try(var a=account();var b=account();var gateway=new PlatformGateway(url,key,temp.resolve("restart-outbox"),GameRegistry.builtIn());var server=NettyMultiGameServer.startPlatform("127.0.0.1",0,GameRegistry.builtIn(),true,gateway,s->gateway.observer())){
        Room r=allocate(a,b,"snake");Ticket ta=a.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class),tb=b.http.call("POST","/api/rooms/"+r.id()+"/ticket",null,Ticket.class);
        try(var p1=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),r.id(),"snake","1","snake/1",ta.credential(),null);var p2=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),r.id(),"snake","1","snake/1",tb.credential(),null)){
            p1.ready().get(6,TimeUnit.SECONDS);p2.ready().get(6,TimeUnit.SECONDS);await(()->p1.latest()!=null&&p1.latest().getStatus().equals("RUNNING"));RoomActor room=server.rooms().require(r.id());int savedPort=port;context.close();
            for(int i=0;i<50&&room.metrics().status().equals("RUNNING");i++){room.debug(true,60).get(3,TimeUnit.SECONDS);}await(()->gateway.queued()>0);boot(savedPort);
            assertEquals(a.id().toString(),a.http.call("GET","/api/me",null,Profile.class).userId());await(()->gateway.delivered()==1);assertEquals("FINISHED",a.http.call("GET","/api/matches/"+r.matchId(),null,Match.class).status());
        }
    }}
    @Test @Order(11) void databasePoolFailureReturns503AndRestartRecovers() throws Exception {try(var a=account()){
        context.getBean(DataSource.class).unwrap(com.zaxxer.hikari.HikariDataSource.class).close();failure(503,()->a.http.call("GET","/api/me",null,Profile.class));int saved=port;context.close();boot(saved);assertEquals(a.id().toString(),a.http.call("GET","/api/me",null,Profile.class).userId());
    }}
    @Test @Order(12) void guestSeesOnlyBoundedSummaryAndAllRoomActionsStillRequireLogin() throws Exception {try(var a=account();var guest=new PlatformHttpClient(url)){
        Room room=a.http.call("POST","/api/rooms",new CreateRoom("snake"),Room.class);var path="/api/public/lobby";
        var overview=guest.call("GET",path,null,LobbyOverview.class);assertEquals(3,overview.games().size());assertTrue(overview.rooms().size()<=128);
        assertTrue(overview.rooms().stream().anyMatch(r->r.id().equals(room.id())&&r.players()==1&&r.capacity()==2&&r.status().equals("OPEN")));
        String json=PlatformHttpClient.JSON.writeValueAsString(overview);for(String secret:List.of("hostId","userId","matchId","members","credential","serverId","password","seed")){assertFalse(json.contains(secret),secret);}
        failure(401,()->guest.call("GET","/api/rooms",null,Room[].class));failure(401,()->guest.call("GET","/api/rooms/"+room.id(),null,Room.class));
        for(String action:List.of("join","ready","ticket","leave")){failure(401,()->guest.call("POST","/api/rooms/"+room.id()+"/"+action,null,Room.class));}
        failure(401,()->guest.call("POST","/api/rooms",new CreateRoom("tank"),Room.class));failure(401,()->guest.call("POST",path,null,LobbyOverview.class));
        a.http.call("POST","/api/rooms/"+room.id()+"/leave",null,Void.class);
        await(()->guest.call("GET",path,null,LobbyOverview.class).rooms().stream().noneMatch(r->r.id().equals(room.id())));
        guest.token("expired-or-invalid");assertNotNull(guest.call("GET",path,null,LobbyOverview.class));
    }}
    @Test @Order(13) void publicLobbyReadHasAnIndependentRateLimit(){try(var guest=new PlatformHttpClient(url)){
        boolean limited=false;for(int i=0;i<245;i++){try{guest.call("GET","/api/public/lobby",null,LobbyOverview.class);}catch(PlatformHttpClient.Failure f){assertEquals(429,f.status());assertEquals("LOBBY_RATE_LIMIT",f.getMessage());limited=true;break;}}
        assertTrue(limited);failure(401,()->guest.call("GET","/api/rooms",null,Room[].class));
    }}
}
