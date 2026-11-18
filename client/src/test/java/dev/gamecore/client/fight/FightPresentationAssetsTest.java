package dev.gamecore.client.fight;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 验证真实舞台/问候透明素材和展示字体字重，不以文件名宣称图片/字体规格。 */
class FightPresentationAssetsTest {
    @Test void actualGreetingRegionsAndStageAreUsableAndWrongAtlasIsRejected() throws Exception {
        String json;try(var in=getClass().getResourceAsStream("/fighters/fighters-greetings-v1.json")){assertNotNull(in);json=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        var clips=FightSpriteLayout.readGreetings(json);assertEquals(16,clips.size());
        try(var in=getClass().getResourceAsStream("/fighters/fighters-greetings-v1.png")){var image=javax.imageio.ImageIO.read(in);assertTrue(image.getColorModel().hasAlpha());for(var c:clips){assertTrue(c.x()+c.width()<=image.getWidth());assertTrue(c.y()+c.height()<=image.getHeight());assertTrue(c.height()>180&&c.width()>180);}}
        try(var in=getClass().getResourceAsStream("/backgrounds/fight-stage-v1.png")){var image=javax.imageio.ImageIO.read(in);assertTrue(image.getWidth()>image.getHeight());assertTrue(image.getWidth()>=1280);}
        assertThrows(RuntimeException.class,()->FightSpriteLayout.readBoxing(json));assertThrows(RuntimeException.class,()->FightSpriteLayout.readGreetings(json.replaceFirst("\"pose\": 0","\"pose\": 8")));
    }
    @Test void displayFontIsActualHeavyWeightAndBodyRemainsRegular() throws Exception {
        assertEquals(900,weight("display-font-file.txt"));assertEquals(450,weight("font-file.txt"));
    }
    private int weight(String manifest) throws Exception {
        String file;try(var in=getClass().getResourceAsStream("/fonts/"+manifest)){assertNotNull(in);file=new String(in.readAllBytes(),StandardCharsets.UTF_8).strip();}
        byte[] bytes;try(var in=getClass().getResourceAsStream("/fonts/"+file)){assertNotNull(in);bytes=in.readAllBytes();}
        var b=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);int tables=Short.toUnsignedInt(b.getShort(4));
        for(int i=0;i<tables;i++){int offset=12+i*16;if(b.getInt(offset)==0x4f532f32){return Short.toUnsignedInt(b.getShort(b.getInt(offset+8)+4));}}
        throw new AssertionError("OS/2 weight table missing");
    }
}
