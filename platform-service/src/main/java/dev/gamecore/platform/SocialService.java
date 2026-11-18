package dev.gamecore.platform;
import dev.gamecore.platform.api.PlatformMessages.*;
import org.springframework.stereotype.Service;
import java.util.*;
import static dev.gamecore.platform.ApiProblem.require;
/** 双向好友关系用规范有序主键避免反向重复；邀请只传房间引用，不代替加入权限/容量校验。 */
@Service
public final class SocialService {
    private final LobbyService lobby;
    public SocialService(LobbyService lobby){this.lobby=lobby;}
    public List<Friend> friends(UUID uid){return lobby.db.query("SELECT u.id,u.username,p.display_name,f.status,f.requester<>?,EXISTS(SELECT 1 FROM login_sessions s WHERE s.user_id=u.id AND s.expires_at>now() AND s.last_seen>now()-interval '60 seconds') FROM friendships f JOIN users u ON u.id=CASE WHEN f.low_id=? THEN f.high_id ELSE f.low_id END JOIN player_profiles p ON p.user_id=u.id WHERE (f.low_id=? OR f.high_id=?) ORDER BY p.display_name LIMIT 200",(r,n)->new Friend(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getBoolean(6)),uid,uid,uid,uid);}
    public void request(UUID uid,String username){String name=AuthService.username(username);lobby.tx.executeWithoutResult(s->{lobby.lock();var users=lobby.db.queryForList("SELECT id FROM users WHERE username=? AND NOT disabled",UUID.class,name);require(!users.isEmpty(),404,"PLAYER_NOT_FOUND");UUID other=users.getFirst();require(!uid.equals(other),400,"SELF_FRIEND");
        if(lobby.db.queryForObject("SELECT count(*) FROM friendships WHERE low_id=least(?::uuid,?::uuid) AND high_id=greatest(?::uuid,?::uuid)",Long.class,uid,other,uid,other)>0){return;}
        for(UUID member:List.of(uid,other)){require(lobby.db.queryForObject("SELECT count(*) FROM friendships WHERE low_id=? OR high_id=?",Long.class,member,member)<200,409,"FRIEND_LIMIT");}
        lobby.db.update("INSERT INTO friendships(low_id,high_id,requester,status) VALUES(least(?::uuid,?::uuid),greatest(?::uuid,?::uuid),?,'PENDING') ON CONFLICT DO NOTHING",uid,other,uid,other,uid);});}
    public void accept(UUID uid,UUID other){require(lobby.db.update("UPDATE friendships SET status='ACCEPTED' WHERE low_id=least(?::uuid,?::uuid) AND high_id=greatest(?::uuid,?::uuid) AND requester=? AND status='PENDING'",uid,other,uid,other,other)==1,409,"NO_FRIEND_REQUEST");}
    public void remove(UUID uid,UUID other){lobby.db.update("DELETE FROM friendships WHERE low_id=least(?::uuid,?::uuid) AND high_id=greatest(?::uuid,?::uuid)",uid,other,uid,other);}
    public void invite(UUID uid,String room,UUID other){LobbyService.roomId(room);lobby.tx.executeWithoutResult(s->{lobby.lock();lobby.member(uid,room);require(lobby.room(room).status().equals("OPEN"),409,"ROOM_LOCKED");
        require(lobby.db.queryForObject("SELECT count(*) FROM friendships WHERE low_id=least(?::uuid,?::uuid) AND high_id=greatest(?::uuid,?::uuid) AND status='ACCEPTED'",Long.class,uid,other,uid,other)==1,403,"FRIEND_REQUIRED");
        lobby.db.update("INSERT INTO room_invitations(room_id,from_id,to_id,expires_at) VALUES(?,?,?,now()+interval '2 minutes') ON CONFLICT(room_id,to_id) DO UPDATE SET from_id=excluded.from_id,expires_at=excluded.expires_at",room,uid,other);});}
    public List<Invitation> invitations(UUID uid){return lobby.db.query("SELECT i.room_id,p.display_name,r.game_id,(extract(epoch FROM i.expires_at)*1000)::bigint FROM room_invitations i JOIN rooms r ON r.id=i.room_id JOIN player_profiles p ON p.user_id=i.from_id WHERE i.to_id=? AND i.expires_at>now() AND r.status='OPEN' ORDER BY i.expires_at DESC LIMIT 100",(r,n)->new Invitation(r.getString(1),r.getString(2),r.getString(3),r.getLong(4)),uid);}
}
