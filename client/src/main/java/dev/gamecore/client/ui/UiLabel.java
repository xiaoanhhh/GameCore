package dev.gamecore.client.ui;

/** 把字号倍率解释为逻辑尺寸比例，而不是字形纹理比例；高密度创建和原位换字体都保持同一排版。
 * 字体由窗口资源管理，控件只记住自身倍率，不能修改共享字体的基础 scale 或释放图集。
 */
public final class UiLabel extends com.badlogic.gdx.scenes.scene2d.ui.Label {
    private float relativeX=1,relativeY=1;
    private boolean initialized;

    public UiLabel(CharSequence text,LabelStyle style){
        super(text,style);initialized=true;applyScale();
    }
    @Override public void setStyle(LabelStyle style){
        super.setStyle(style);
        // Label 构造器会调用 setStyle，此时本类字段还没有完成构造。
        if(initialized){applyScale();}
    }
    @Override public void setFontScale(float scaleX,float scaleY){relativeX=scaleX;relativeY=scaleY;if(initialized){applyScale();}}
    @Override public void setFontScaleX(float scale){setFontScale(scale,relativeY);}
    @Override public void setFontScaleY(float scale){setFontScale(relativeX,scale);}
    @Override public float getFontScaleX(){return relativeX;}
    @Override public float getFontScaleY(){return relativeY;}
    private void applyScale(){var data=getStyle().font.getData();super.setFontScale(data.scaleX*relativeX,data.scaleY*relativeY);}
}
