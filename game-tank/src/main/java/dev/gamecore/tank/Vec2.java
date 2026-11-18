package dev.gamecore.tank;

/** 以地图格为单位的不可变向量；逻辑坐标原点在左下角，不接受非有限值。 */
public record Vec2(double x, double y) {
    public Vec2 {
        if (!Double.isFinite(x) || !Double.isFinite(y)) { throw new IllegalArgumentException("Non-finite vector"); }
    }
    public Vec2 add(Vec2 other) { return new Vec2(x + other.x, y + other.y); }
    public Vec2 scale(double value) { return new Vec2(x * value, y * value); }
    public double distanceSquared(Vec2 other) { double dx = x - other.x, dy = y - other.y; return dx * dx + dy * dy; }
    public static Vec2 direction(double degrees) {
        double radians = StrictMath.toRadians(degrees);
        return new Vec2(StrictMath.cos(radians), StrictMath.sin(radians));
    }
}

/**
 * public record承载不可变数据
 * 有compact Constructor用于校验
 * 自动补全全参数构造方法
 * 不可被继承
 * Vec2为自定义二维向量类型
 * add：为加法
 * scale：每个分量乘以倍率，用来把方向转换为位移或速度
 * distanceSquared用于计算欧式距离
 * direction用于根据角度计算单位向量
 */
