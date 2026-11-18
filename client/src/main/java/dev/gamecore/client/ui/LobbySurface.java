package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

/**
 * 大厅切角面板：右上/左下各切一刀的共享九宫格，外层描边色 + 内缩填充色构成边框；
 * 可选左侧强调脊线（横幅、选中项）。只借用窗口共享纹理，不拥有释放权，绘制后恢复 Batch 颜色。
 */
final class LobbySurface extends BaseDrawable {
    private final NinePatchDrawable fill,edge;
    private final UiInk ink;
    private final float stroke;
    private final Color spine;
    LobbySurface(ThemeManager theme,Color face,Color border,float stroke,Color spine){
        fill=new NinePatchDrawable(theme.assets.cut).tint(face);edge=new NinePatchDrawable(theme.assets.cut).tint(border);
        ink=new UiInk(theme);this.stroke=stroke;this.spine=spine;
        setLeftWidth(14);setRightWidth(14);setTopHeight(10);setBottomHeight(10);
    }
    @Override public void draw(Batch b,float x,float y,float w,float h){
        float packed=b.getPackedColor(),a=b.getColor().a;
        edge.draw(b,x,y,w,h);
        if(w>2*stroke&&h>2*stroke){fill.draw(b,x+stroke,y+stroke,w-2*stroke,h-2*stroke);}
        // 顶沿极淡高光给表面一点体积；避开右上切角，小控件不画以免断线。
        if(w>64&&h>28){ink.line(b,LobbyPalette.TEXT,a*.05f,x+stroke+2,y+h-stroke-.5f,x+w-12,y+h-stroke-.5f,1);}
        if(spine!=null&&h>20){ink.rect(b,spine,a,x+stroke,y+h*.18f,3,h*.64f);}
        b.setPackedColor(packed);
    }
}
