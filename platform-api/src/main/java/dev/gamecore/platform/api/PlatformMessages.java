package dev.gamecore.platform.api;

import java.util.List;

/** 平台HTTP的稳定值契约；没有密码回显、数据库实体、Channel或可变World。 */
public final class PlatformMessages {
    private PlatformMessages() { }
    public record Credentials(String username, String password) { @Override public String toString(){return "Credentials[REDACTED]";} }
    public record Login(String token, long expiresAt, Profile profile) { @Override public String toString(){return "Login[REDACTED]";} }
    public record Profile(String userId, String username, String displayName, String role) { }
    public record Rename(String displayName) { }
    public record Game(String id, String version, String rulesVersion, int players, int tps) { }
    public record Member(String userId, String displayName, int slot, boolean ready, boolean online) { }
    public record Room(String id, String gameId, String hostId, String status, List<Member> members, String matchId) { }
    /** 游客目录只含公开房间摘要，不携带账户、成员、比赛ID、凭证或节点地址。 */
    public record RoomSummary(String id,String gameId,String status,int players,int capacity,int readyPlayers) {
        public RoomSummary {
            if(id==null||!id.matches("r-[a-f0-9]{32}")||gameId==null||!gameId.matches("[a-z0-9-]{1,32}")
                    ||!java.util.Set.of("OPEN","ALLOCATED").contains(status)||capacity<1||capacity>8
                    ||players<0||players>capacity||readyPlayers<0||readyPlayers>players){throw new IllegalArgumentException("Invalid room summary");}
        }
    }
    public record LobbyOverview(List<Game> games,List<RoomSummary> rooms) {
        public LobbyOverview {
            games=List.copyOf(games);rooms=List.copyOf(rooms);
            if(games.size()>16||rooms.size()>128||games.stream().anyMatch(g->g.id()==null||g.version()==null||g.rulesVersion()==null||g.players()<1||g.players()>8)){
                throw new IllegalArgumentException("Lobby capacity/identity");
            }
        }
    }
    public record CreateRoom(String gameId) { }
    public record Ready(boolean ready) { }
    public record FriendRequest(String username) { }
    public record Friend(String userId, String username, String displayName, String status, boolean incoming, boolean online) { }
    public record Invitation(String roomId, String fromUser, String gameId, long expiresAt) { }
    public record Invite(String userId) { }
    public record Ticket(String host, int port, String roomId, String gameId, String gameVersion,
                         String rulesVersion, String matchId, int slot, String credential, long expiresAt, Boolean tls) {
        // 兼容旧平台省略字段；只在这个新增可选字段归一化，不放宽数值/其他必填字段校验。
        public Ticket { tls=Boolean.TRUE.equals(tls); }
        public Ticket(String host,int port,String roomId,String gameId,String gameVersion,String rulesVersion,String matchId,int slot,String credential,long expiresAt){this(host,port,roomId,gameId,gameVersion,rulesVersion,matchId,slot,credential,expiresAt,false);}
        @Override public String toString(){return "Ticket[REDACTED]";}
    }
    public record Redeem(String credential, String connectionId) { @Override public String toString(){return "Redeem[REDACTED]";} }
    public record Admission(String roomId, String gameId, String gameVersion, String rulesVersion,
                            String matchId, int slot, String userId, long seed) { }
    public record Finish(String matchId, String reason, String snapshot) { }
    /** 仅认证节点可沿用原连接申请下一局；不向玩家发第二份身份或复用旧结果ID。 */
    public record NextMatch(String matchId) { }
    public record Match(String id, String gameId, String status, String outcome, long tick, long endedAt, List<Participant> participants) { }
    public record Participant(String userId, String displayName, int slot, int points, String result) { }
    public record Rank(String userId, String displayName, long games, long wins, double winRate, long points) { }
    public record Replay(String matchId, String reference, String stateHash, long expiresAt) { }
    public record ApiError(String code, String message) { }
}
