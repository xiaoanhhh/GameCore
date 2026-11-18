package dev.gamecore.platform;
import dev.gamecore.platform.api.PlatformMessages.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.gamecore.platform.ApiProblem.require;
/** PostgreSQL会话可跨平台重启保留；单会话退出与全设备退出均立即撤销数据库凭证。 */
@Service
public final class AuthService {
    record Identity(UUID id,String role) { }
    final JdbcTemplate db;final TransactionTemplate tx;
    private final PasswordEncoder encoder=PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final String dummy=encoder.encode("unusable-dummy-password");
    public AuthService(JdbcTemplate db,PlatformTransactionManager tm){this.db=db;tx=new TransactionTemplate(tm);}
    static UUID id(String value){try{return UUID.fromString(value);}catch(RuntimeException e){throw new ApiProblem(400,"INVALID_ID");}}
    static String username(String value){require(value!=null&&value.matches("[A-Za-z0-9_]{3,20}"),400,"INVALID_USERNAME");return value.toLowerCase(Locale.ROOT);}
    private static void password(String value){require(value!=null&&value.length()>=10&&value.getBytes(StandardCharsets.UTF_8).length<=72,400,"PASSWORD_LENGTH");}
    public Profile register(Credentials body){String name=username(body.username());password(body.password());String hash=encoder.encode(body.password());
        return tx.execute(s->{UUID uid=UUID.randomUUID();db.update("INSERT INTO users(id,username,password_hash) VALUES(?,?,?)",uid,name,hash);
            db.update("INSERT INTO player_profiles(user_id,display_name) VALUES(?,?)",uid,name);return profile(uid);});}
    public Login login(Credentials body){String name=username(body.username());password(body.password());
        var rows=db.queryForList("SELECT id,password_hash,disabled FROM users WHERE username=?",name);
        boolean valid=encoder.matches(body.password(),rows.isEmpty()?dummy:(String)rows.getFirst().get("password_hash"));
        require(!rows.isEmpty()&&valid&&!Boolean.TRUE.equals(rows.getFirst().get("disabled")),401,"LOGIN_FAILED");
        UUID uid=(UUID)rows.getFirst().get("id");return tx.execute(s->{
            db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",UUID.class,uid);
            db.update("DELETE FROM login_sessions WHERE user_id=? AND (expires_at<now() OR last_seen<now()-interval '30 minutes')",uid);
            // 每账户至多5个会话，清最早会话。过期记录另有定时全局清理。
            db.update("DELETE FROM login_sessions WHERE token_hash IN (SELECT token_hash FROM login_sessions WHERE user_id=? ORDER BY last_seen DESC OFFSET 4)",uid);
            String token=Tokens.create();long expires=db.queryForObject("INSERT INTO login_sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '12 hours') RETURNING (extract(epoch FROM expires_at)*1000)::bigint",Long.class,Tokens.hash(token),uid);
            return new Login(token,expires,profile(uid));});}
    Identity authenticate(String token){
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}")){return null;}
        var rows=db.query("UPDATE login_sessions s SET last_seen=now() FROM users u WHERE s.token_hash=? AND s.user_id=u.id AND NOT u.disabled AND s.expires_at>now() AND s.last_seen>now()-interval '30 minutes' RETURNING u.id,u.role",(r,n)->new Identity(r.getObject(1,UUID.class),r.getString(2)),Tokens.hash(token));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Profile profile(UUID uid){return db.queryForObject("SELECT u.id,u.username,p.display_name,u.role FROM users u JOIN player_profiles p ON p.user_id=u.id WHERE u.id=?",(r,n)->new Profile(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),uid);}
    public Profile rename(UUID uid,Rename body){String v=body.displayName();require(v!=null&&!v.isBlank()&&v.length()<=32&&v.codePoints().noneMatch(Character::isISOControl),400,"INVALID_NAME");db.update("UPDATE player_profiles SET display_name=? WHERE user_id=?",v.strip(),uid);return profile(uid);}
    public void logout(UUID uid,String token,boolean all){tx.executeWithoutResult(s->{if(all){db.update("DELETE FROM login_sessions WHERE user_id=?",uid);}else{db.update("DELETE FROM login_sessions WHERE user_id=? AND token_hash=?",uid,Tokens.hash(token));}db.update("DELETE FROM join_tickets WHERE user_id=? AND NOT consumed",uid);});}
}
