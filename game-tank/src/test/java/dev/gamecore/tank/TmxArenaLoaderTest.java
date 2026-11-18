package dev.gamecore.tank;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static dev.gamecore.tank.TankFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TmxArenaLoaderTest {
    private String source() throws IOException {
        try (var stream = getClass().getResourceAsStream("/maps/tank/crossfire.tmx")) {
            assertNotNull(stream); return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private TankMap parse(String xml) throws IOException { return TmxArenaLoader.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))); }
    @Test void bothProductionMapsLoadWithoutGraphicsAndHaveNavigableSpawns() throws IOException {
        for (String id : List.of("crossfire", "ruins")) {
            var map = arena(id); assertEquals(id, map.id()); assertEquals(24, map.width()); assertEquals(18, map.height());
            assertEquals(new Vec2(2.5, 2.5), map.spawns().getFirst().position()); assertEquals(new Vec2(21.5, 15.5), map.spawns().get(1).position());
            assertEquals(1, map.repairZones().size()); assertTrue(map.repairZones().getFirst().contains(new Vec2(12, 9)));
            assertFalse(GridPathfinder.find(map, map.spawns().getFirst().position(), map.spawns().get(1).position()).isEmpty());
            assertFalse(GridPathfinder.find(map, map.spawns().getFirst().position(), new Vec2(11.5, 8.5)).isEmpty());
        }
    }
    @Test void tiledTopDownCoordinatesBecomeBottomUpLogicalGrid() throws IOException {
        var map = arena("crossfire"); assertTrue(map.wall(6, 3)); assertFalse(map.wall(6, 14));
        assertTrue(map.wall(-1, 4)); assertTrue(map.wall(24, 4));
        assertEquals(8, map.repairZones().getFirst().y());
    }
    @Test void visualLayersDoNotDefineRuleCollision() throws IOException {
        String source = source(); int from = source.indexOf("<layer id=\"2\""); int to = source.indexOf("</layer>", from);
        String changed = source.substring(0, from) + source.substring(from, to).replace("2,", "0,") + source.substring(to);
        assertTrue(parse(changed).wall(6, 3));
    }
    @Test void malformedUnsupportedOrAmbiguousMapsAreRejected() throws IOException {
        String xml = source();
        for (String invalid : List.of(xml.replace("infinite=\"0\"", "infinite=\"1\""), xml.replace("orthogonal", "isometric"),
                xml.replace("encoding=\"csv\"", "encoding=\"base64\""), xml.replace("name=\"collision\"", "name=\"floor\""),
                xml.replace("name=\"p2\"", "name=\"p1\""), xml.replace("x=\"80.0\"", "x=\"0\""),
                xml.replace("<point/>", "<ellipse/>"), xml.replace("type=\"repair\"", "type=\"unknown\""),
                xml.replace("name=\"collision\"", "name=\"collision\" offsetx=\"32\""), xml.replace("1,1,1,", "1,1,"))) {
            assertThrows(IOException.class, () -> parse(invalid), invalid.substring(0, Math.min(invalid.length(), 150)));
        }
    }
    @Test void externalEntitiesAndOversizedDocumentsAreRejected() throws IOException {
        String xml = source().replace("<map version", "<!DOCTYPE map [<!ENTITY ext SYSTEM 'file:///unavailable'>]><map version");
        assertThrows(IOException.class, () -> parse(xml)); assertThrows(IOException.class, () -> parse(" ".repeat(1_048_577)));
    }
    @Test void mapOwnsCopiesAndRejectsInvalidSpawns() {
        boolean[] walls = new boolean[64]; var spawns = List.of(new TankMap.Spawn(new Vec2(1.5, 1.5), 0), new TankMap.Spawn(new Vec2(6.5, 6.5), 0));
        var map = new TankMap("test", 8, 8, walls, spawns, List.of()); walls[2 * 8 + 2] = true; assertFalse(map.wall(2, 2));
        assertThrows(UnsupportedOperationException.class, () -> map.spawns().clear());
        assertThrows(IllegalArgumentException.class, () -> open(8, 8, new Vec2(.1, .1), new Vec2(6, 6)));
        assertThrows(IllegalArgumentException.class, () -> open(8, 8, new Vec2(2, 2), new Vec2(2.1, 2.1)));
    }
}
