package dev.gamecore.tank;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 逻辑地图的规范摘要；供服务器与客户端核对同源几何，不含纹理或网络依赖。 */
public final class TankMapFingerprint {
    private TankMapFingerprint() { }
    public static String digest(TankMap map) {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeUTF(map.id()); out.writeInt(map.width()); out.writeInt(map.height());
            for (int y = 0; y < map.height(); y++) { for (int x = 0; x < map.width(); x++) { out.writeBoolean(map.wall(x, y)); } }
            for (var spawn : map.spawns()) { out.writeDouble(spawn.position().x()); out.writeDouble(spawn.position().y()); out.writeDouble(spawn.heading()); }
            out.writeInt(map.repairZones().size());
            for (var zone : map.repairZones()) { out.writeDouble(zone.x()); out.writeDouble(zone.y()); out.writeDouble(zone.width()); out.writeDouble(zone.height()); }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
