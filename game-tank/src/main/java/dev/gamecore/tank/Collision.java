package dev.gamecore.tank;

/** 连续线段碰撞工具；返回 [0,1] 首次命中比例，无命中返回正无穷，防止子弹跨过目标。 */
public final class Collision {
    private Collision() { }
    public static double circle(Vec2 start, Vec2 end, Vec2 center, double radius) {
        double dx = end.x() - start.x(), dy = end.y() - start.y();
        double ox = start.x() - center.x(), oy = start.y() - center.y();
        double c = ox * ox + oy * oy - radius * radius;
        if (c <= 0) { return 0; }
        double a = dx * dx + dy * dy;
        if (a == 0) { return Double.POSITIVE_INFINITY; }
        double b = 2 * (ox * dx + oy * dy), discriminant = b * b - 4 * a * c;
        if (discriminant < 0) { return Double.POSITIVE_INFINITY; }
        double t = (-b - StrictMath.sqrt(discriminant)) / (2 * a);
        return t >= 0 && t <= 1 ? t : Double.POSITIVE_INFINITY;
    }
    public static double box(Vec2 start, Vec2 end, double minX, double minY, double maxX, double maxY) {
        double lo = 0, hi = 1;
        double[] origin = {start.x(), start.y()}, delta = {end.x() - start.x(), end.y() - start.y()};
        double[] min = {minX, minY}, max = {maxX, maxY};
        for (int axis = 0; axis < 2; axis++) {
            if (delta[axis] == 0) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) { return Double.POSITIVE_INFINITY; }
            } else {
                double first = (min[axis] - origin[axis]) / delta[axis], last = (max[axis] - origin[axis]) / delta[axis];
                lo = Math.max(lo, Math.min(first, last)); hi = Math.min(hi, Math.max(first, last));
                if (lo > hi) { return Double.POSITIVE_INFINITY; }
            }
        }
        return lo;
    }
    public static double wall(TankMap map, Vec2 start, Vec2 end, double radius) {
        double first = Double.POSITIVE_INFINITY;
        int minX = Math.max(-1, (int) StrictMath.floor(Math.min(start.x(), end.x()) - radius));
        int maxX = Math.min(map.width(), (int) StrictMath.floor(Math.max(start.x(), end.x()) + radius));
        int minY = Math.max(-1, (int) StrictMath.floor(Math.min(start.y(), end.y()) - radius));
        int maxY = Math.min(map.height(), (int) StrictMath.floor(Math.max(start.y(), end.y()) + radius));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (map.wall(x, y)) { first = Math.min(first, box(start, end, x - radius, y - radius, x + 1 + radius, y + 1 + radius)); }
            }
        }
        return first;
    }
}
