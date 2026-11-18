package dev.gamecore.tank;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 纯 JDK 的有限 TMX 契约：正交有限地图、CSV 格层、点出生层、矩形玩法层。
 * 拒绝外部实体、翻转 GID、压缩/无限地图和未知层，避免客户端看见的地图与服务端规则悄悄不同。
 * 调用方持有输入流生命周期；解析器不加载 tileset 图片，不需要 OpenGL。
 */
public final class TmxArenaLoader {
    private TmxArenaLoader() { }
    public static TankMap load(InputStream input) throws IOException {
        try {
            byte[] bytes = input.readNBytes(1_048_577);
            if (bytes.length > 1_048_576) { throw new IOException("TMX exceeds 1 MiB"); }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder(); builder.setErrorHandler(new DefaultHandler());
            Element root = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            require(root.getTagName().equals("map") && root.getAttribute("orientation").equals("orthogonal"), "Only orthogonal maps supported");
            require(!root.getAttribute("infinite").equals("1"), "Infinite map unsupported");
            int width = integer(root, "width"), height = integer(root, "height");
            int tile = integer(root, "tilewidth");
            require(width >= 4 && height >= 4 && width <= 128 && height <= 128 && tile > 0
                    && tile == integer(root, "tileheight"), "Invalid dimensions");
            Map<String, Element> layers = new HashMap<>();
            for (Element child : children(root)) {
                switch (child.getTagName()) {
                    case "layer", "objectgroup" -> {
                        require(layers.putIfAbsent(child.getAttribute("name"), child) == null, "Duplicate layer");
                        require(numberOr(child, "offsetx", 0) == 0 && numberOr(child, "offsety", 0) == 0
                                && numberOr(child, "x", 0) == 0 && numberOr(child, "y", 0) == 0, "Layer offsets unsupported");
                    }
                    case "properties", "tileset" -> { }
                    default -> throw new IllegalArgumentException("Unsupported map element: " + child.getTagName());
                }
            }
            require(layers.keySet().equals(java.util.Set.of("floor", "walls", "decorations", "collision", "spawns", "gameplay")), "Six named layers required");
            // 可视层也校验格式，但不把可视图块误当作碰撞规则。
            csv(layers.get("floor"), width, height); csv(layers.get("walls"), width, height); csv(layers.get("decorations"), width, height);
            int[] collision = csv(layers.get("collision"), width, height);
            boolean[] walls = new boolean[width * height];
            for (int row = 0; row < height; row++) {
                for (int x = 0; x < width; x++) { walls[(height - row - 1) * width + x] = collision[row * width + x] != 0; }
            }
            TankMap.Spawn[] spawns = new TankMap.Spawn[2];
            for (Element object : children(layers.get("spawns"))) {
                require(object.getTagName().equals("object") && child(object, "point") != null, "Spawn must be point object");
                require(numberOr(object, "rotation", 0) == 0, "Use heading property for spawn rotation");
                int index = switch (object.getAttribute("name")) { case "p1" -> 0; case "p2" -> 1; default -> throw new IllegalArgumentException("Unknown spawn name"); };
                require(spawns[index] == null, "Duplicate spawn");
                spawns[index] = new TankMap.Spawn(new Vec2(number(object, "x") / tile, height - number(object, "y") / tile),
                        Double.parseDouble(properties(object).getOrDefault("heading", "0")));
            }
            require(spawns[0] != null && spawns[1] != null, "Missing player spawn");
            List<TankMap.RepairZone> zones = new ArrayList<>();
            for (Element object : children(layers.get("gameplay"))) {
                require(object.getTagName().equals("object") && "repair".equals(object.getAttribute("type"))
                        && numberOr(object, "rotation", 0) == 0, "Only axis-aligned repair rectangles supported");
                for (Element extra : children(object)) { require(extra.getTagName().equals("properties"), "Unsupported zone shape"); }
                double zoneHeight = number(object, "height") / tile;
                zones.add(new TankMap.RepairZone(number(object, "x") / tile, height - number(object, "y") / tile - zoneHeight,
                        number(object, "width") / tile, zoneHeight));
            }
            return new TankMap(properties(root).getOrDefault("arenaId", "arena"), width, height, walls, List.of(spawns), zones);
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException | IllegalArgumentException exception) {
            throw new IOException("Invalid Tank Arena TMX: " + exception.getMessage(), exception);
        }
    }
    private static int[] csv(Element layer, int width, int height) {
        require(layer.getTagName().equals("layer") && integer(layer, "width") == width && integer(layer, "height") == height, "Invalid tile layer dimensions");
        Element data = child(layer, "data");
        require(data != null && data.getAttribute("encoding").equals("csv") && data.getAttribute("compression").isEmpty(), "Only uncompressed CSV supported");
        String[] parts = data.getTextContent().strip().split("\\s*,\\s*", -1);
        require(parts.length == width * height, "Wrong CSV cell count");
        int[] cells = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            cells[i] = Integer.parseInt(parts[i].strip()); require(cells[i] >= 0 && cells[i] <= 3, "Only palette GIDs 0..3 supported");
        }
        return cells;
    }
    private static Map<String, String> properties(Element parent) {
        Map<String, String> result = new HashMap<>(); Element props = child(parent, "properties");
        if (props != null) {
            for (Element prop : children(props)) {
                require(prop.getTagName().equals("property") && result.putIfAbsent(prop.getAttribute("name"), prop.getAttribute("value")) == null, "Invalid properties");
            }
        }
        return result;
    }
    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) { if (node instanceof Element element) { result.add(element); } }
        return result;
    }
    private static Element child(Element parent, String tag) {
        return children(parent).stream().filter(e -> e.getTagName().equals(tag)).findFirst().orElse(null);
    }
    private static int integer(Element e, String attr) { return Integer.parseInt(e.getAttribute(attr)); }
    private static double number(Element e, String attr) { return Double.parseDouble(e.getAttribute(attr)); }
    private static double numberOr(Element e, String attr, double fallback) { return e.hasAttribute(attr) ? number(e, attr) : fallback; }
    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalArgumentException(message); } }
}
