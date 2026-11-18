package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import dev.gamecore.client.assets.ClientAssetManager.FontChange;
import java.util.function.UnaryOperator;

/** 原位替换组件样式中的字体，不重建页面，因此保留草稿、光标、焦点和模式选择。 */
public final class UiFontRefresh {
    private UiFontRefresh() { }

    public static void apply(Actor actor, FontChange change) {
        apply(actor,change::replacement);
    }
    /** 同一控件更新也用于无 GL 的真实控件回归；只替换样式引用，资源释放仍由 FontChange 负责。 */
    static void apply(Actor actor,UnaryOperator<BitmapFont> replacement){
        if (actor instanceof Window window) {
            Window.WindowStyle style = new Window.WindowStyle(window.getStyle());
            style.titleFont = replacement.apply(style.titleFont);
            window.setStyle(style);
        } else if (actor instanceof TextButton button) {
            TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(button.getStyle());
            style.font = replacement.apply(style.font);
            button.setStyle(style);
        } else if (actor instanceof Label label) {
            Label.LabelStyle style = new Label.LabelStyle(label.getStyle());
            style.font = replacement.apply(style.font);
            label.setStyle(style);
        } else if (actor instanceof TextField field) {
            TextField.TextFieldStyle style = new TextField.TextFieldStyle(field.getStyle());
            style.font = replacement.apply(style.font);
            style.messageFont = replacement.apply(style.messageFont);
            field.setStyle(style);
        } else if (actor instanceof SelectBox<?> box) {
            SelectBox.SelectBoxStyle style = new SelectBox.SelectBoxStyle(box.getStyle());
            style.font = replacement.apply(style.font);
            style.listStyle = listStyle(style.listStyle, replacement);
            box.setStyle(style);
        } else if (actor instanceof List<?> list) {
            list.setStyle(listStyle(list.getStyle(), replacement));
        }
        if (actor instanceof Group group) {
            // 样式替换不增删 Actor；同样处理已打开菜单中挂到 Stage 的列表。
            for (Actor child : group.getChildren()) { apply(child, replacement); }
        }
    }

    private static List.ListStyle listStyle(List.ListStyle previous,UnaryOperator<BitmapFont> replacement) {
        List.ListStyle style = new List.ListStyle(previous);
        BitmapFont next = replacement.apply(style.font);
        style.font = next;
        return style;
    }
}
