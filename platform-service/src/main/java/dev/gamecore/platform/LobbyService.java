package dev.gamecore.platform;
import dev.gamecore.platform.api.PlatformMessages.*;
import dev.gamecore.online.GameRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static dev.gamecore.platform.ApiProblem.require;
/** 大厅写入使用同一事务锁和数据库唯一约束；Room只保存业务准备状态，不运行游戏模拟。 */
@Service
public final class LobbyService {
    final JdbcTemplate db;final TransactionTemplate tx;final GameRegistry games=GameRegistry.builtIn();
    final String serverId,host;final int port;
    @Value("${platform.game-tls:false}") private boolean tls;
    private LobbyOverview publicCache;private long publicCacheUntil;
    public LobbyService(JdbcTemplate db,PlatformTransactionManager tm,@Value("${platform.server-id}") String serverId,
            @Value("${platform.game-host}") String host,@Value("${platform.game-port}") int port){
        require(serverId.matches("[a-zA-Z0-9-]{1,40}")&&host.length()<=128&&port>0&&port<=65535,500,"SERVER_CONFIG");
        this.db=db;tx=new TransactionTemplate(tm);this.serverId=serverId;this.host=host;this.port=port;
    }
    void lock(){db.queryForObject("SELECT pg_advisory_xact_lock(9009)",Object.class);}
    static String roomId(String v){require(v!=null&&v.matches("r-[a-f0-9]{32}"),400,"INVALID_ROOM");return v;}
    public List<Game> games(){return games.all().stream().map(f->{var t=f.type();return new Game(t.id(),t.gameVersion(),t.rulesVersion(),t.maxPlayers(),t.defaultRoomTps());}).toList();}
    /** 单实例一秒有界只读缓存；一次聚合查询代替游客逐房读取成员/会话，不执行清理或业务写入。 */
    public synchronized LobbyOverview preview(){
        long now=System.nanoTime();if(publicCache!=null&&now<publicCacheUntil){return publicCache;}
        var rooms=db.query("WITH visible AS (SELECT id,game_id,status FROM rooms WHERE status IN ('OPEN','ALLOCATED') ORDER BY created_at,id LIMIT 128) SELECT r.id,r.game_id,r.status,count(m.user_id)::int,count(m.user_id) FILTER(WHERE m.ready)::int FROM visible r LEFT JOIN room_members m ON m.room_id=r.id GROUP BY r.id,r.game_id,r.status ORDER BY r.id",
            (rs,n)->new RoomSummary(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4),games.require(rs.getString(2)).type().maxPlayers(),rs.getInt(5)));
        publicCache=new LobbyOverview(games(),rooms);publicCacheUntil=System.nanoTime()+1_000_000_000L;return publicCache;
    }
    public List<Room> rooms(){return tx.execute(s->db.queryForList("SELECT id FROM rooms WHERE status IN ('OPEN','ALLOCATED') ORDER BY created_at LIMIT 128",String.class).stream().map(this::room).toList());}
    public Room room(String id){var rows=db.queryForList("SELECT * FROM rooms WHERE id=?",roomId(id));require(!rows.isEmpty(),404,"ROOM_NOT_FOUND");var r=rows.getFirst();
        var members=db.query("SELECT m.user_id,p.display_name,m.slot,m.ready,EXISTS(SELECT 1 FROM login_sessions s WHERE s.user_id=m.user_id AND s.expires_at>now() AND s.last_seen>now()-interval '60 seconds') FROM room_members m JOIN player_profiles p ON p.user_id=m.user_id WHERE m.room_id=? ORDER BY m.slot",(rs,n)->new Member(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getBoolean(4),rs.getBoolean(5)),id);
        return new Room(id,(String)r.get("game_id"),r.get("host_id").toString(),(String)r.get("status"),members,r.get("match_id")==null?"":r.get("match_id").toString());}
    public Room create(UUID uid,CreateRoom body){var factory=games.require(body.gameId());return tx.execute(s->{lock();cleanup();
        var existing=db.queryForList("SELECT room_id FROM room_members WHERE user_id=?",String.class,uid);
        require(existing.isEmpty(),409,"ALREADY_IN_ROOM");
        require(db.queryForObject("SELECT count(*) FROM rooms WHERE status IN ('OPEN','ALLOCATED')",Long.class)<128,409,"ROOM_LIMIT");
        String id="r-"+UUID.randomUUID().toString().replace("-","");var t=factory.type();
        db.update("INSERT INTO rooms(id,game_id,game_version,rules_version,host_id,server_id) VALUES(?,?,?,?,?,?)",id,t.id(),t.gameVersion(),t.rulesVersion(),uid,serverId);
        db.update("INSERT INTO room_members(user_id,room_id,slot) VALUES(?,?,0)",uid,id);return room(id);});}
    public Room join(UUID uid,String id){roomId(id);return tx.execute(s->{lock();cleanup();var r=room(id);
        if(r.members().stream().anyMatch(m->m.userId().equals(uid.toString()))){return r;}
        require(r.status().equals("OPEN")&&r.members().size()<2,409,"ROOM_UNAVAILABLE");
        require(db.queryForObject("SELECT count(*) FROM room_members WHERE user_id=?",Long.class,uid)==0,409,"ALREADY_IN_ROOM");
        int slot=r.members().stream().anyMatch(m->m.slot()==0)?1:0;
        db.update("INSERT INTO room_members(user_id,room_id,slot) VALUES(?,?,?)",uid,id,slot);touch(id);return room(id);});}
    void member(UUID uid,String id){require(db.queryForObject("SELECT count(*) FROM room_members WHERE user_id=? AND room_id=?",Long.class,uid,id)==1,403,"NOT_ROOM_MEMBER");}
    void touch(String id){db.update("UPDATE rooms SET touched_at=now() WHERE id=?",id);}
    public Room ready(UUID uid,String id,boolean ready){roomId(id);return tx.execute(s->{lock();cleanup();member(uid,id);var r=room(id);require(r.status().equals("OPEN"),409,"ROOM_LOCKED");
        db.update("UPDATE room_members SET ready=? WHERE user_id=? AND room_id=?",ready,uid,id);touch(id);
        if(db.queryForObject("SELECT count(*) FROM room_members WHERE room_id=? AND ready",Long.class,id)==2){
            UUID match=UUID.randomUUID();long seed=new java.security.SecureRandom().nextLong();
            db.update("INSERT INTO match_records(id,room_id,server_id,game_id,game_version,rules_version,seed) SELECT ?,id,server_id,game_id,game_version,rules_version,? FROM rooms WHERE id=?",match,seed,id);
            db.update("INSERT INTO match_participants(match_id,user_id,slot) SELECT ?,user_id,slot FROM room_members WHERE room_id=?",match,id);
            db.update("UPDATE rooms SET status='ALLOCATED',match_id=?,touched_at=now() WHERE id=?",match,id);
        }return room(id);});}
    public void leave(UUID uid,String id){roomId(id);tx.executeWithoutResult(s->{lock();member(uid,id);var r=room(id);
        if(r.status().equals("ALLOCATED")){abort(id,"PLAYER_LEFT");return;}
        db.update("DELETE FROM room_members WHERE user_id=? AND room_id=?",uid,id);var left=room(id).members();
        if(left.isEmpty()){db.update("UPDATE rooms SET status='CLOSED' WHERE id=?",id);}else{db.update("UPDATE rooms SET host_id=?,touched_at=now() WHERE id=?",AuthService.id(left.getFirst().userId()),id);db.update("UPDATE room_members SET ready=false WHERE room_id=?",id);}
    });}
    public Ticket ticket(UUID uid,String id){roomId(id);return tx.execute(s->{lock();cleanup();member(uid,id);var r=room(id);require(r.status().equals("ALLOCATED"),409,"NOT_READY");
        UUID match=AuthService.id(r.matchId());var rows=db.queryForList("SELECT consumed FROM join_tickets WHERE match_id=? AND user_id=?",match,uid);
        require(rows.isEmpty()||!Boolean.TRUE.equals(rows.getFirst().get("consumed")),409,"TICKET_ALREADY_USED");
        String credential=Tokens.create();int slot=r.members().stream().filter(m->m.userId().equals(uid.toString())).findFirst().orElseThrow().slot();
        long expires=db.queryForObject("INSERT INTO join_tickets(token_hash,match_id,user_id,slot,expires_at) VALUES(?,?,?,?,now()+interval '60 seconds') ON CONFLICT(match_id,user_id) DO UPDATE SET token_hash=excluded.token_hash,expires_at=excluded.expires_at RETURNING (extract(epoch FROM expires_at)*1000)::bigint",Long.class,Tokens.hash(credential),match,uid,slot);
        var t=games.require(r.gameId()).type();return new Ticket(host,port,id,t.id(),t.gameVersion(),t.rulesVersion(),r.matchId(),slot,credential,expires,tls);
    });}
    public Admission redeem(Redeem body){require(body.credential()!=null&&body.credential().matches("[A-Za-z0-9_-]{43}")&&body.connectionId()!=null&&body.connectionId().matches("[a-zA-Z0-9-]{1,64}"),400,"INVALID_TICKET");
        return tx.execute(s->{lock();cleanup();var rows=db.queryForList("SELECT t.*,m.room_id,m.game_id,m.game_version,m.rules_version,m.seed,m.server_id,m.status FROM join_tickets t JOIN match_records m ON m.id=t.match_id JOIN rooms r ON r.id=m.room_id WHERE t.token_hash=? AND t.expires_at>now() AND NOT t.consumed AND m.status='ALLOCATED' AND r.status='ALLOCATED' AND NOT EXISTS(SELECT 1 FROM users u WHERE u.id=t.user_id AND u.disabled)",Tokens.hash(body.credential()));
            require(!rows.isEmpty(),401,"TICKET_EXPIRED_OR_USED");var r=rows.getFirst();require(serverId.equals(r.get("server_id")),403,"WRONG_SERVER");
            db.update("UPDATE join_tickets SET consumed=true,connection_id=? WHERE token_hash=?",body.connectionId(),Tokens.hash(body.credential()));
            return new Admission((String)r.get("room_id"),(String)r.get("game_id"),(String)r.get("game_version"),(String)r.get("rules_version"),r.get("match_id").toString(),(Integer)r.get("slot"),r.get("user_id").toString(),(Long)r.get("seed"));});
    }
    public boolean active(String match){boolean active=db.queryForObject("SELECT count(*) FROM match_records m JOIN rooms r ON r.id=m.room_id WHERE m.id=? AND m.status='ALLOCATED' AND r.status='ALLOCATED'",Long.class,AuthService.id(match))==1;if(active){db.update("UPDATE rooms SET touched_at=now() WHERE match_id=? AND status='ALLOCATED'",AuthService.id(match));db.update("UPDATE login_sessions s SET last_seen=now() WHERE s.expires_at>now() AND EXISTS(SELECT 1 FROM match_participants p JOIN join_tickets t ON t.match_id=p.match_id AND t.user_id=p.user_id WHERE p.match_id=? AND p.user_id=s.user_id AND t.consumed)",AuthService.id(match));}return active;}
    void abort(String id,String reason){
        db.update("UPDATE match_records SET status='ABORTED',reason=?,ended_at=now() WHERE room_id=? AND status='ALLOCATED'",reason,id);
        db.update("UPDATE match_participants SET result='ABORTED' WHERE match_id IN(SELECT match_id FROM rooms WHERE id=?) AND result='PENDING'",id);
        db.update("DELETE FROM room_members WHERE room_id=?",id);db.update("UPDATE rooms SET status='CLOSED',touched_at=now() WHERE id=?",id);
    }
    void cleanup(){
        // 开放房间10分钟且无人在线，分配后2分钟尚未两张凭证兑换，视为遗弃。
        var stale=db.queryForList("SELECT r.id FROM rooms r WHERE (status='OPEN' AND touched_at<now()-interval '10 minutes' AND NOT EXISTS(SELECT 1 FROM room_members m JOIN login_sessions s ON s.user_id=m.user_id WHERE m.room_id=r.id AND s.expires_at>now() AND s.last_seen>now()-interval '60 seconds')) OR (status='ALLOCATED' AND (SELECT m.created_at FROM match_records m WHERE m.id=r.match_id)<now()-interval '2 minutes' AND (SELECT count(*) FROM join_tickets t WHERE t.match_id=r.match_id AND consumed)<2) OR (status='ALLOCATED' AND touched_at<now()-interval '5 minutes' AND (SELECT count(*) FROM join_tickets t WHERE t.match_id=r.match_id AND consumed)=2)",String.class);
        stale.forEach(id->abort(id,"ADMISSION_TIMEOUT"));
    }
}
