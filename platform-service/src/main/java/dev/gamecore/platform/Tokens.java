package dev.gamecore.platform;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** 标准SecureRandom不透明凭证；SHA-256只用于随机凭证索引，绝不用于密码哈希。 */
final class Tokens {
    private static final SecureRandom RANDOM=new SecureRandom();
    private Tokens() { }
    static String create(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    static String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static boolean same(String a,String b){return MessageDigest.isEqual(hash(a).getBytes(StandardCharsets.US_ASCII),hash(b).getBytes(StandardCharsets.US_ASCII));}
}
