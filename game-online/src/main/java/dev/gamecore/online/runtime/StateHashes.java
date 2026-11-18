package dev.gamecore.online.runtime;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
/** 哈希覆盖规范编码的公开语义状态；不是完整JVM内存或跨平台确定性的证明。 */
public final class StateHashes {
    private StateHashes() { }
    public static byte[] hash(String game,String rules,byte[] snapshot) {
        try { var digest=MessageDigest.getInstance("SHA-256"); digest.update((game+"\n"+rules+"\n").getBytes(StandardCharsets.UTF_8)); return digest.digest(snapshot); }
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static String hex(byte[] hash){return HexFormat.of().formatHex(hash);}
}
