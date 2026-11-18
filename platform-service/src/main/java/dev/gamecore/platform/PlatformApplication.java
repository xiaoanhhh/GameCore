package dev.gamecore.platform;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/** 模块化单体入口：账户/社交/大厅/战绩共用事务数据库；不拥有游戏Tick。 */
@SpringBootApplication(excludeName="org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class PlatformApplication {
    public static void main(String[] args){SpringApplication.run(PlatformApplication.class,args);}
}
