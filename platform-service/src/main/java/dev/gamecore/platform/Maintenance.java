package dev.gamecore.platform;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
/** 清理失效业务会话；异常交给下一次执行重试，不影响已在运行的游戏Tick。 */
@Configuration
@EnableScheduling
public class Maintenance {
    private final LobbyService lobby;
    public Maintenance(LobbyService lobby){this.lobby=lobby;}
    @Scheduled(fixedDelay=30000) public void cleanup(){try{lobby.tx.executeWithoutResult(s->{lobby.lock();lobby.cleanup();
        lobby.db.update("DELETE FROM login_sessions WHERE expires_at<now() OR last_seen<now()-interval '30 minutes'");
        lobby.db.update("DELETE FROM room_invitations WHERE expires_at<now()");
        lobby.db.update("DELETE FROM join_tickets WHERE expires_at<now()-interval '1 day'");
        lobby.db.update("DELETE FROM replay_metadata WHERE expires_at<now()");
    });}catch(org.springframework.dao.DataAccessException ignored){ /* 数据库恢复后重试；HTTP会明确返回503。 */ }}
}
