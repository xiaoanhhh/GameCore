package dev.gamecore.client.effects;

import dev.gamecore.api.event.GameEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端专用 ECS 学习实验：实体只有 ID，位置/速度/寿命/外观按组件存储，系统查询组件交集。
 * 不使用 libGDX、不进入 TankGame；事件创建实体，运动/寿命系统推进，渲染只读快照。
 * 最多 384 粒子，满额淘汰最早实体；暂停由页面停止推进，重开/离页清理所有组件。
 */
public final class EffectWorld {
    public static final int MAX_PARTICLES = 384;
    private record Position(double x, double y) { }
    private record Velocity(double x, double y) { }
    private record Lifetime(double remaining, double total) { }
    private record Appearance(double size, GameEvent.Effect.Kind kind) { }
    public record Particle(long entity, double x, double y, double size, double alpha, GameEvent.Effect.Kind kind) { }
    private final Map<Long, Position> positions = new LinkedHashMap<>();
    private final Map<Long, Velocity> velocities = new LinkedHashMap<>();
    private final Map<Long, Lifetime> lifetimes = new LinkedHashMap<>();
    private final Map<Long, Appearance> appearances = new LinkedHashMap<>();
    private long nextEntity;

    public void accept(GameEvent event) {
        if (!(event instanceof GameEvent.Effect effect)) { return; }
        int count = switch (effect.kind()) { case SHOT -> 5; case DESTROYED -> 24; case HIT -> 12; default -> 6; };
        for (int i = 0; i < count; i++) {
            if (positions.size() >= MAX_PARTICLES) { remove(positions.keySet().iterator().next()); }
            long entity = nextEntity++;
            boolean muzzle = effect.kind() == GameEvent.Effect.Kind.SHOT;
            double angle = StrictMath.toRadians(muzzle ? effect.heading() - 20 + 40.0 * i / (count - 1) : 360.0 * i / count);
            double heading = StrictMath.toRadians(effect.heading());
            double speed = muzzle ? 2.2 : .8 + (i % 4) * .45;
            double life = effect.kind() == GameEvent.Effect.Kind.DESTROYED ? .8 : muzzle ? .18 : .45;
            positions.put(entity, new Position(effect.x() + (muzzle ? .6 * StrictMath.cos(heading) : 0),
                    effect.y() + (muzzle ? .6 * StrictMath.sin(heading) : 0)));
            velocities.put(entity, new Velocity(StrictMath.cos(angle) * speed, StrictMath.sin(angle) * speed));
            lifetimes.put(entity, new Lifetime(life, life));
            appearances.put(entity, new Appearance(muzzle ? .12 : .18, effect.kind()));
        }
    }
    public void update(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) { throw new IllegalArgumentException("Invalid effect delta"); }
        List<Long> expired = new ArrayList<>();
        // 系统只处理同时拥有位置、速度和寿命的实体；到期后统一删除，避免迭代期间改结构。
        for (var entry : lifetimes.entrySet()) {
            long id = entry.getKey(); var life = entry.getValue();
            var position = positions.get(id); var velocity = velocities.get(id);
            if (position == null || velocity == null || life.remaining() <= seconds) { expired.add(id); continue; }
            positions.put(id, new Position(position.x() + velocity.x() * seconds, position.y() + velocity.y() * seconds));
            entry.setValue(new Lifetime(life.remaining() - seconds, life.total()));
        }
        expired.forEach(this::remove);
    }
    public List<Particle> snapshot() {
        List<Particle> result = new ArrayList<>();
        positions.forEach((id, position) -> {
            var life = lifetimes.get(id); var look = appearances.get(id);
            if (life != null && look != null) { result.add(new Particle(id, position.x(), position.y(), look.size(), life.remaining() / life.total(), look.kind())); }
        });
        return List.copyOf(result);
    }
    private void remove(long id) { positions.remove(id); velocities.remove(id); lifetimes.remove(id); appearances.remove(id); }
    public int size() { return positions.size(); }
    public int componentCount() { return positions.size() + velocities.size() + lifetimes.size() + appearances.size(); }
    public void clear() { positions.clear(); velocities.clear(); lifetimes.clear(); appearances.clear(); }
}
