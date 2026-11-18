package dev.gamecore.platform;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
/** 无Cookie的Bearer API；不启用跨域。服务端密钥仅准入internal接口，与玩家会话完全隔离。 */
@Configuration
public class SecurityConfiguration {
    @Bean SecurityFilterChain chain(HttpSecurity http,AuthService auth,@Value("${platform.server-key}") String key,@Value("${platform.auth-limit:30}") int authLimit,@Value("${platform.preview-limit:240}") int previewLimit) throws Exception {
        if(key.length()<32){throw new IllegalStateException("GAME_SERVER_KEY requires at least 32 characters; no default secret");}
        return http.csrf(c->c.disable()).cors(c->c.disable()).httpBasic(c->c.disable()).formLogin(c->c.disable())
            .logout(c->c.disable()).requestCache(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/api/auth/register","/api/auth/login","/api/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/public/lobby").permitAll()
                .requestMatchers("/api/internal/**").hasRole("SERVER").requestMatchers("/api/**").hasAnyRole("USER","ADMIN").anyRequest().denyAll())
            .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->error(r,401,"UNAUTHORIZED")).accessDeniedHandler((q,r,x)->error(r,403,"FORBIDDEN")))
            .addFilterBefore(new Gate(auth,key,authLimit,previewLimit),AnonymousAuthenticationFilter.class).build();
    }
    static void error(HttpServletResponse response,int status,String code) throws IOException {response.setStatus(status);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":\""+code+"\",\"message\":\"Request failed\"}");}
    private static final class Gate extends OncePerRequestFilter {
        private final AuthService auth;private final String key;private final Budget authBudget,previewBudget;
        Gate(AuthService auth,String key,int limit,int previewLimit){this.auth=auth;this.key=key;authBudget=new Budget(limit);previewBudget=new Budget(previewLimit);}
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
            // 在Jackson读取前限制整个请求体，包括chunked请求；不在日志回显原始JSON。
            var limited=new HttpServletRequestWrapper(req){@Override public ServletInputStream getInputStream() throws IOException {
                ServletInputStream in=super.getInputStream();return new ServletInputStream(){int remaining=98304;
                    @Override public int read() throws IOException{if(--remaining<0){throw new IOException("BODY_LIMIT");}return in.read();}
                    @Override public boolean isFinished(){return in.isFinished();}@Override public boolean isReady(){return in.isReady();}
                    @Override public void setReadListener(ReadListener l){in.setReadListener(l);}};}};
            if(req.getContentLengthLong()>98304){error(res,413,"BODY_LIMIT");return;}
            String path=req.getServletPath();
            if(path.startsWith("/api/auth/")&&!authBudget.allowed(req.getRemoteAddr())){error(res,429,"AUTH_RATE_LIMIT");return;}
            if(path.equals("/api/public/lobby")&&req.getMethod().equals("GET")){
                if(!previewBudget.allowed(req.getRemoteAddr())){error(res,429,"LOBBY_RATE_LIMIT");return;}
                // 公开摘要不消费Bearer、不更新会话；过期登录态仍可以安全浏览。
                chain.doFilter(limited,res);return;
            }
            try {
                if(path.startsWith("/api/internal/")){
                    String supplied=req.getHeader("X-Game-Server-Key");
                    if(supplied!=null&&supplied.length()<=256&&Tokens.same(key,supplied)){
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("game-server",null,List.of(new SimpleGrantedAuthority("ROLE_SERVER"))));}
                }
                if(SecurityContextHolder.getContext().getAuthentication()==null){
                    String supplied=req.getHeader("Authorization");
                    if(supplied!=null&&supplied.startsWith("Bearer ")){var identity=auth.authenticate(supplied.substring(7));if(identity!=null){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity,null,List.of(new SimpleGrantedAuthority("ROLE_"+identity.role()))));}}
                }
            }catch(org.springframework.dao.DataAccessException e){error(res,503,"DATABASE_UNAVAILABLE");return;}
            chain.doFilter(limited,res);
        }
    }
    /** 两类请求独立窗口，每类最多1024来源，拒绝新来源时不淘汰旧计数绕过限制。 */
    static final class Budget {
        private final int limit;private final Map<String,Integer> attempts=new HashMap<>();private long window=System.nanoTime();
        Budget(int limit){if(limit<1||limit>1000){throw new IllegalArgumentException("HTTP rate limit");}this.limit=limit;}
        synchronized boolean allowed(String address){long now=System.nanoTime();if(now-window>60_000_000_000L){attempts.clear();window=now;}if(attempts.size()>=1024&&!attempts.containsKey(address)){return false;}return attempts.merge(address,1,Integer::sum)<=limit;}
    }
}
