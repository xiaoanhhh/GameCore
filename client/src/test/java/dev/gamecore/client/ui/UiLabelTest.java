package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 用有实际字宽/行高的内存字体测量 Scene2D 排版；无需纹理、显示器或 mock 掉 Label 的布局实现。 */
class UiLabelTest {
    private static final String COPY="AAAA AAAA AAAA AAAA";
    private static BitmapFont font(float density){
        int raster=UiFontScale.rasterSize(14,density);float factor=raster/14f;
        var data=new BitmapFont.BitmapFontData();data.lineHeight=14*factor;data.capHeight=10*factor;
        data.ascent=0;data.descent=-2*factor;data.down=-14*factor;data.spaceXadvance=6*factor;
        var result=new BitmapFont(data,Array.with(new TextureRegion()),false);
        for(char c:new char[]{'A',' '}){var glyph=new BitmapFont.Glyph();glyph.id=c;glyph.width=c==' '?0:Math.round(6*factor);
            glyph.height=c==' '?0:Math.round(10*factor);glyph.xadvance=Math.round(7*factor);data.setGlyph(c,glyph);}
        data.setScale(14f/raster);return result;
    }
    private static Label label(BitmapFont font){return new ThemeManager(null).label(COPY,font,Color.WHITE);}
    @ParameterizedTest @ValueSource(floats={1,1.25f,1.5f,2,3,4})
    void labelCreatedAtHighDensityKeepsLogicalCaptionHeight(float density){
        var font=font(density);var label=label(font);label.setFontScale(.82f);
        assertEquals(11.48f,label.getPrefHeight(),.0001f);assertEquals(.82f,label.getFontScaleX(),.0001f);
        assertEquals(14f/UiFontScale.rasterSize(14,density),font.getData().scaleX,.0001f);
    }
    @Test void fontReplacementKeepsCaptionSizeThroughRepeatedGrowAndShrink(){
        var old=font(1);var label=label(old);label.setFontScale(.82f);float height=label.getPrefHeight();
        for(float density:new float[]{1.25f,1.5f,2,3,4,2,1,1.5f,1}){
            var next=font(density);var prior=old;UiFontRefresh.apply(label,f->f==prior?next:f);
            assertSame(next,label.getStyle().font);assertEquals(height,label.getPrefHeight(),.0001f);assertEquals(.82f,label.getFontScaleY(),.0001f);old=next;
        }
    }
    @Test void wrappedSummaryStaysInsideItsAllocatedHeightAfterFontRefresh(){
        var old=font(1);var summary=label(old);summary.setFontScale(.9f);summary.setWrap(true);summary.setSize(80,42);summary.validate();
        float height=summary.getGlyphLayout().height;var next=font(2);UiFontRefresh.apply(summary,f->f==old?next:f);summary.validate();
        assertEquals(height,summary.getGlyphLayout().height,1);assertTrue(summary.getGlyphLayout().height<=summary.getHeight());
        assertTrue(summary.getGlyphLayout().width<=summary.getWidth());
    }
    @Test void independentAxesAndNeighboringLabelsDoNotChangeSharedFontScale(){
        var font=font(2);var one=label(font);var two=label(font);one.setFontScale(.8f,.9f);one.setFontScaleX(.7f);two.setFontScale(1.1f);
        assertEquals(.7f,one.getFontScaleX());assertEquals(.9f,one.getFontScaleY());assertEquals(12.6f,one.getPrefHeight(),.0001f);
        assertEquals(15.4f,two.getPrefHeight(),.0001f);one.validate();two.validate();assertEquals(.5f,font.getData().scaleX);assertEquals(.5f,font.getData().scaleY);
    }
    @Test void recursiveRefreshPreservesActorIdentityTextAndLabelStyleOwnership(){
        var old=font(1);var next=font(2);var root=new Group();var caption=label(old);caption.setFontScale(.82f);root.addActor(caption);
        var style=new Label.LabelStyle(old,Color.WHITE);var nested=new Group();var second=new UiLabel("AAAA",style);nested.addActor(second);root.addActor(nested);
        UiFontRefresh.apply(root,f->f==old?next:f);
        assertSame(caption,root.getChildren().get(0));assertEquals(COPY,caption.getText().toString());assertEquals(11.48f,caption.getPrefHeight(),.0001f);
        assertSame(next,second.getStyle().font);assertSame(old,style.font);
    }
}
