package dev.gamecore.platform;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PublicLobbyBudgetTest {
    @Test void authAndBrowseBudgetsAreIndependentAndBounded(){var auth=new SecurityConfiguration.Budget(1);var browse=new SecurityConfiguration.Budget(2);
        assertTrue(auth.allowed("same"));assertFalse(auth.allowed("same"));assertTrue(browse.allowed("same"));assertTrue(browse.allowed("same"));assertFalse(browse.allowed("same"));
        var bounded=new SecurityConfiguration.Budget(1);for(int i=0;i<1024;i++){assertTrue(bounded.allowed("source-"+i));}assertFalse(bounded.allowed("overflow"));assertFalse(bounded.allowed("source-0"));
    }
}
