package dev.gamecore.platform;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** 纯Java输入/标准随机凭证边界，不用内存数据库冒充PostgreSQL。 */
class AccountRulesTest {
    @Test void credentialsAreRandomAndOnlyTokenHashIsIndexed(){String a=Tokens.create(),b=Tokens.create();assertNotEquals(a,b);assertEquals(43,a.length());assertEquals(64,Tokens.hash(a).length());assertTrue(Tokens.same(a,a));assertFalse(Tokens.same(a,b));}
    @Test void usernamesAreCanonicalAndRejectSqlOrControlCharacters(){assertEquals("alice_2",AuthService.username("Alice_2"));for(String n:new String[]{"a","x' OR 1=1","foo bar","中文账号","abc\n"}){assertThrows(ApiProblem.class,()->AuthService.username(n));}}
    @Test void identifiersAreParsedBeforeDatabaseAccess(){assertThrows(ApiProblem.class,()->AuthService.id("not-uuid"));assertThrows(ApiProblem.class,()->LobbyService.roomId("../other"));}
}
