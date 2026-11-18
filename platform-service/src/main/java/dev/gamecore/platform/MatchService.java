package dev.gamecore.platform;
import dev.gamecore.platform.api.PlatformMessages.*;
import dev.gamecore.api.online.GameSnapshotCodec;
import dev.gamecore.online.runtime.StateHashes;
import dev.gamecore.protocol.GameProtocol;
import org.springframework.stereotype.Service;
import java.util.*;
import static dev.gamecore.platform.ApiProblem.require;
/** 战绩只接受认证游戏服务器；参与者来自分配记录，胜负来自该游戏快照Codec，客户端不能上报积分。 */
@Service
public final class MatchService {
    private final LobbyService lobby;
    public MatchService(LobbyService lobby){this.lobby=lobby;}
    private static <S> String outcome(GameSnapshotCodec<S> codec,byte[] state){return codec.outcome(codec.decode(state));}
    public Match finish(Finish body){UUID match=AuthService.id(body.matchId());require(body.reason()!=null&&Set.of("RULES","PLAYER_DISCONNECTED","ROOM_CLOSED","ROOM_FAILURE","ADMISSION_DISCONNECTED","RESTARTED").contains(body.reason()),400,"INVALID_REASON");
        require(body.snapshot()!=null&&body.snapshot().length()<=88000,400,"SNAPSHOT_LIMIT");
        final GameProtocol.Snapshot snapshot;
        try{snapshot=GameProtocol.Snapshot.parseFrom(Base64.getDecoder().decode(body.snapshot()));}catch(Exception e){throw new ApiProblem(400,"INVALID_SNAPSHOT");}
        String digest=Tokens.hash(body.reason()+":"+body.snapshot());
        return lobby.tx.execute(s->{lobby.lock();var rows=lobby.db.queryForList("SELECT * FROM match_records WHERE id=? FOR UPDATE",match);require(!rows.isEmpty(),404,"MATCH_NOT_FOUND");var row=rows.getFirst();
            require(lobby.serverId.equals(row.get("server_id")),403,"WRONG_SERVER");
            if(!row.get("status").equals("ALLOCATED")){
                // 取消/超时已形成终态，不能被迟到上传重新计分；成功上传严格核对幂等内容。
                require(row.get("submission_hash")==null||digest.equals(row.get("submission_hash")),409,"RESULT_CONFLICT");return match(match);
            }
            if(body.reason().equals("ADMISSION_DISCONNECTED")){
                require(body.snapshot().isEmpty(),400,"UNSTARTED_SNAPSHOT");
                require(lobby.db.queryForObject("SELECT count(*) FROM join_tickets WHERE match_id=? AND consumed",Long.class,match)>=1,409,"NOT_ADMITTED");
                lobby.abort((String)row.get("room_id"),body.reason());lobby.db.update("UPDATE match_records SET submission_hash=? WHERE id=?",digest,match);return match(match);
            }
            var factory=lobby.games.require((String)row.get("game_id"));
            require(factory.type().gameVersion().equals(row.get("game_version"))&&factory.type().rulesVersion().equals(row.get("rules_version")),409,"GAME_VERSION");
            require(snapshot.getMatchId().equals(match.toString())&&snapshot.getRoomId().equals(row.get("room_id"))&&snapshot.getGameId().equals(row.get("game_id"))&&snapshot.getSimulationTick()>=0&&snapshot.getSimulationTick()<10000000,400,"SNAPSHOT_IDENTITY");
            byte[] bytes=snapshot.getState().toByteArray();String result=outcome(factory.snapshots(),bytes);
            byte[] hash=StateHashes.hash(factory.type().id(),factory.type().rulesVersion(),bytes);
            require(java.security.MessageDigest.isEqual(hash,snapshot.getStateHash().toByteArray()),400,"STATE_HASH");
            boolean finished=body.reason().equals("RULES");
            require(!finished||snapshot.getStatus().equals("FINISHED")&&Set.of("PLAYER_ONE","PLAYER_TWO","DRAW").contains(result),400,"NOT_FINISHED");
            require(lobby.db.queryForObject("SELECT count(*) FROM join_tickets WHERE match_id=? AND consumed",Long.class,match)==2,409,"PARTICIPANTS_NOT_ADMITTED");
            String status=finished?"FINISHED":"ABORTED";
            lobby.db.update("UPDATE match_records SET status=?,outcome=?,reason=?,final_tick=?,state_hash=?,submission_hash=?,ended_at=now() WHERE id=?",status,finished?result:"NONE",body.reason(),snapshot.getSimulationTick(),StateHashes.hex(hash),digest,match);
            int winner=result.equals("PLAYER_ONE")?0:result.equals("PLAYER_TWO")?1:-1;
            for(int slot=0;slot<2;slot++){
                String personal=!finished?"ABORTED":winner<0?"DRAW":slot==winner?"WIN":"LOSS";int points=personal.equals("WIN")?3:personal.equals("DRAW")?1:0;
                lobby.db.update("UPDATE match_participants SET result=?,points=? WHERE match_id=? AND slot=?",personal,points,match,slot);
            }
            // 只登记受限逻辑引用；录像文件异步封存成功后才可标记可下载，当前不开放文件HTTP。
            lobby.db.update("INSERT INTO replay_metadata(match_id,reference,state_hash,expires_at) VALUES(?,?,?,now()+interval '7 days')",match,"match/"+match,StateHashes.hex(hash));
            lobby.db.update("UPDATE rooms SET status='FINISHED',touched_at=now() WHERE id=?",row.get("room_id"));lobby.db.update("DELETE FROM room_members WHERE room_id=?",row.get("room_id"));
            return match(match);
        });
    }
    /** 原比赛先持久化终态；幂等后继ID避免网络重试创建多局，不改变数据库Schema。 */
    public NextMatch next(String previous){
        UUID prior=AuthService.id(previous);UUID next=UUID.nameUUIDFromBytes(("gamecore-next:"+prior).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return lobby.tx.execute(s->{
            lobby.lock();var rows=lobby.db.queryForList("SELECT * FROM match_records WHERE id=? FOR UPDATE",prior);require(!rows.isEmpty(),404,"MATCH_NOT_FOUND");var old=rows.getFirst();
            require(lobby.serverId.equals(old.get("server_id")),403,"WRONG_SERVER");
            require(Set.of("FINISHED","ABORTED").contains(old.get("status"))&&old.get("submission_hash")!=null,409,"RESULT_PENDING");
            require(Set.of("RULES","RESTARTED").contains(old.get("reason")),409,"REMATCH_NOT_ALLOWED");
            var existing=lobby.db.queryForList("SELECT status FROM match_records WHERE id=?",next);
            if(!existing.isEmpty()){require(existing.getFirst().get("status").equals("ALLOCATED"),409,"REMATCH_EXPIRED");return new NextMatch(next.toString());}
            require(lobby.db.queryForObject("SELECT count(*) FROM join_tickets WHERE match_id=? AND consumed",Long.class,prior)==2,409,"NOT_ADMITTED");
            require(lobby.db.queryForObject("SELECT count(*) FROM room_members WHERE user_id IN(SELECT user_id FROM match_participants WHERE match_id=?)",Long.class,prior)==0,409,"PLAYERS_IN_OTHER_ROOM");
            require(lobby.db.queryForObject("SELECT count(*) FROM users WHERE id IN(SELECT user_id FROM match_participants WHERE match_id=?) AND disabled",Long.class,prior)==0,403,"ACCOUNT_DISABLED");
            require(lobby.db.queryForObject("SELECT count(*) FROM rooms WHERE id=? AND match_id=? AND status IN('FINISHED','ABORTED','CLOSED')",Long.class,old.get("room_id"),prior)==1,409,"ROOM_CHANGED");
            lobby.db.update("INSERT INTO match_records(id,room_id,server_id,game_id,game_version,rules_version,seed) SELECT ?,room_id,server_id,game_id,game_version,rules_version,seed FROM match_records WHERE id=?",next,prior);
            lobby.db.update("INSERT INTO match_participants(match_id,user_id,slot) SELECT ?,user_id,slot FROM match_participants WHERE match_id=?",next,prior);
            var tickets=lobby.db.queryForList("SELECT user_id,slot,connection_id FROM join_tickets WHERE match_id=? AND consumed",prior);
            for(var ticket:tickets){
                lobby.db.update("INSERT INTO join_tickets(token_hash,match_id,user_id,slot,expires_at,consumed,connection_id) VALUES(?,?,?,?,now()+interval '5 minutes',true,?)",Tokens.hash("bound:"+next+":"+ticket.get("user_id")),next,ticket.get("user_id"),ticket.get("slot"),ticket.get("connection_id"));
            }
            lobby.db.update("INSERT INTO room_members(user_id,room_id,slot,ready) SELECT user_id,?,slot,true FROM match_participants WHERE match_id=?",old.get("room_id"),next);
            lobby.db.update("UPDATE rooms SET status='ALLOCATED',match_id=?,touched_at=now() WHERE id=?",next,old.get("room_id"));
            return new NextMatch(next.toString());
        });
    }
    public Match match(UUID id){var rows=lobby.db.queryForList("SELECT id,game_id,status,outcome,final_tick,coalesce((extract(epoch FROM ended_at)*1000)::bigint,0) AS ended FROM match_records WHERE id=?",id);require(!rows.isEmpty(),404,"MATCH_NOT_FOUND");var r=rows.getFirst();
        var participants=lobby.db.query("SELECT m.user_id,p.display_name,m.slot,m.points,m.result FROM match_participants m JOIN player_profiles p ON p.user_id=m.user_id WHERE match_id=? ORDER BY slot",(rs,n)->new Participant(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getInt(4),rs.getString(5)),id);
        return new Match(id.toString(),(String)r.get("game_id"),(String)r.get("status"),(String)r.get("outcome"),(Long)r.get("final_tick"),(Long)r.get("ended"),participants);
    }
    public Match personal(UUID uid,UUID id){require(lobby.db.queryForObject("SELECT count(*) FROM match_participants WHERE user_id=? AND match_id=?",Long.class,uid,id)==1,403,"NOT_PARTICIPANT");return match(id);}
    public List<Match> history(UUID uid,String game){if(game!=null){lobby.games.require(game);}return lobby.db.queryForList("SELECT m.id FROM match_records m JOIN match_participants p ON p.match_id=m.id WHERE p.user_id=? AND (?::varchar IS NULL OR m.game_id=?) ORDER BY m.created_at DESC LIMIT 50",UUID.class,uid,game,game).stream().map(this::match).toList();}
    public List<Rank> ranks(String game){lobby.games.require(game);return lobby.db.query("SELECT p.user_id,n.display_name,count(*) AS games,count(*) FILTER (WHERE p.result='WIN') AS wins,sum(p.points) AS points FROM match_participants p JOIN match_records m ON m.id=p.match_id JOIN player_profiles n ON n.user_id=p.user_id WHERE m.game_id=? AND m.status='FINISHED' GROUP BY p.user_id,n.display_name ORDER BY points DESC,wins DESC,p.user_id LIMIT 100",(r,n)->new Rank(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),(double)r.getLong(4)/r.getLong(3),r.getLong(5)),game);}
}
