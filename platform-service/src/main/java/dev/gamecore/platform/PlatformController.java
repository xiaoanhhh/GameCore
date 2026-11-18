package dev.gamecore.platform;
import dev.gamecore.platform.api.PlatformMessages.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
/** HTTP组合边界；业务服务不依赖Servlet。所有分页采用有界默认窗口，不返回密码/会话实体。 */
@RestController
@RequestMapping("/api")
public final class PlatformController {
    private final AuthService auth;private final LobbyService lobby;private final SocialService social;private final MatchService matches;
    public PlatformController(AuthService a,LobbyService l,SocialService s,MatchService m){auth=a;lobby=l;social=s;matches=m;}
    private static UUID user(Authentication a){return ((AuthService.Identity)a.getPrincipal()).id();}
    @GetMapping("/health") Map<String,String> health(){lobby.db.queryForObject("SELECT 1",Integer.class);return Map.of("status","UP");}
    @GetMapping("/public/lobby") LobbyOverview preview(){return lobby.preview();}
    @PostMapping("/auth/register") Profile register(@RequestBody Credentials b){return auth.register(b);}
    @PostMapping("/auth/login") Login login(@RequestBody Credentials b){return auth.login(b);}
    @PostMapping("/auth/logout") void logout(Authentication a,HttpServletRequest r){auth.logout(user(a),r.getHeader("Authorization").substring(7),false);}
    @PostMapping("/auth/logout-all") void logoutAll(Authentication a){auth.logout(user(a),"",true);}
    @GetMapping("/me") Profile me(Authentication a){return auth.profile(user(a));}
    @PutMapping("/me") Profile rename(Authentication a,@RequestBody Rename b){return auth.rename(user(a),b);}
    @GetMapping("/games") List<Game> games(){return lobby.games();}
    @GetMapping("/rooms") List<Room> rooms(){return lobby.rooms();}
    @PostMapping("/rooms") Room create(Authentication a,@RequestBody CreateRoom b){return lobby.create(user(a),b);}
    @GetMapping("/rooms/{id}") Room room(@PathVariable String id){return lobby.room(id);}
    @PostMapping("/rooms/{id}/join") Room join(Authentication a,@PathVariable String id){return lobby.join(user(a),id);}
    @PostMapping("/rooms/{id}/ready") Room ready(Authentication a,@PathVariable String id,@RequestBody Ready b){return lobby.ready(user(a),id,b.ready());}
    @PostMapping("/rooms/{id}/leave") void leave(Authentication a,@PathVariable String id){lobby.leave(user(a),id);}
    @PostMapping("/rooms/{id}/ticket") Ticket ticket(Authentication a,@PathVariable String id){return lobby.ticket(user(a),id);}
    @GetMapping("/friends") List<Friend> friends(Authentication a){return social.friends(user(a));}
    @PostMapping("/friends") void request(Authentication a,@RequestBody FriendRequest b){social.request(user(a),b.username());}
    @PostMapping("/friends/{id}/accept") void accept(Authentication a,@PathVariable String id){social.accept(user(a),AuthService.id(id));}
    @DeleteMapping("/friends/{id}") void remove(Authentication a,@PathVariable String id){social.remove(user(a),AuthService.id(id));}
    @PostMapping("/rooms/{id}/invite") void invite(Authentication a,@PathVariable String id,@RequestBody Invite b){social.invite(user(a),id,AuthService.id(b.userId()));}
    @GetMapping("/invitations") List<Invitation> invitations(Authentication a){return social.invitations(user(a));}
    @GetMapping("/history") List<Match> history(Authentication a,@RequestParam(required=false) String game){return matches.history(user(a),game);}
    @GetMapping("/matches/{id}") Match match(Authentication a,@PathVariable String id){return matches.personal(user(a),AuthService.id(id));}
    @GetMapping("/ranks/{game}") List<Rank> ranks(@PathVariable String game){return matches.ranks(game);}
    @PostMapping("/internal/tickets/redeem") Admission redeem(@RequestBody Redeem b){return lobby.redeem(b);}
    @PostMapping("/internal/matches/{id}/active") Map<String,Boolean> active(@PathVariable String id){return Map.of("active",lobby.active(id));}
    @PostMapping("/internal/results") Match result(@RequestBody Finish b){return matches.finish(b);}
    @PostMapping("/internal/matches/{id}/next") NextMatch next(@PathVariable String id){return matches.next(id);}
    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(ApiProblem.class) ResponseEntity<ApiError> problem(ApiProblem p){return ResponseEntity.status(p.status).body(new ApiError(p.getMessage(),"请求未完成，请检查操作或稍后重试"));}
        @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> conflict(){return ResponseEntity.status(409).body(new ApiError("CONFLICT","数据已存在或状态已改变"));}
        @ExceptionHandler(DataAccessException.class) ResponseEntity<ApiError> unavailable(){return ResponseEntity.status(503).body(new ApiError("DATABASE_UNAVAILABLE","平台暂不可用，本地游戏仍可使用"));}
        @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,NullPointerException.class}) ResponseEntity<ApiError> invalid(){return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST","请求数据无效"));}
    }
}
