package dev.gamecore.client.ui;

/** 将内容的左下坐标转换为 ScrollPane 从顶部计量的偏移；可见元素不移动，超高元素对齐顶部。 */
public final class ScrollReveal {
    private ScrollReveal() { }
    public static float offset(float current, float maximum, float viewport, float y, float height) {
        float bottom = maximum - current;
        float next = current;
        if (height > viewport || y + height > bottom + viewport) { next = maximum - y - height + viewport; }
        else if (y < bottom) { next = maximum - y; }
        return Math.max(0, Math.min(maximum, next));
    }
}
