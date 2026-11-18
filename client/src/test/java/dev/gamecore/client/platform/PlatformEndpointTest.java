package dev.gamecore.client.platform;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlatformEndpointTest {
    @Test void publisherEnvironmentAndDeveloperOverrideHaveExplicitPrecedence(){var bundled=new Properties();bundled.setProperty("platform.url","https://platform.example.com");
        assertEquals("https://platform.example.com",PlatformEndpoint.resolve(Map.of(),Map.of(),bundled).url());
        assertEquals("http://127.0.0.1:18080",PlatformEndpoint.resolve(Map.of(),Map.of("GAMECORE_PLATFORM_URL","http://127.0.0.1:18080"),bundled).url());
        assertEquals("https://override.example.com",PlatformEndpoint.resolve(Map.of("gamecore.platform.url","https://override.example.com"),Map.of("GAMECORE_PLATFORM_URL","https://env.example.com"),bundled).url());
        assertFalse(PlatformEndpoint.resolve(Map.of("gamecore.platform.url",""),Map.of(),bundled).configured());
    }
    @Test void unconfiguredOfflineAndInvalidEndpointsNeverCreateAClient(){
        var absent=PlatformEndpoint.resolve(Map.of(),Map.of(),new Properties());assertFalse(absent.configured());
        for(String value:List.of("http://example.com","https://name:password@example.com","https://example.com/path","file:///tmp/test","https://example.com/?token=bad")){
            var bad=PlatformEndpoint.resolve(Map.of("gamecore.platform.url",value),Map.of(),new Properties());assertFalse(bad.configured());assertFalse(bad.problem().contains("password"));
        }
    }
}
