package dev.gamecore.fight;

/** 有意保留反应间隔的确定性陪练；只读取与玩家相同的快照，不修改世界、不预知未来输入。 */
public final class FightBot {
    private final int phase;
    public FightBot(long seed) { phase = Math.floorMod(seed, 997); }
    public FightCommand command(FightSnapshot view, int player) {
        var self = view.fighters().get(player); var target = view.fighters().get(1 - player);
        double distance = Math.abs(target.x() - self.x());
        long time = view.tick() + phase;
        boolean danger = view.projectiles().stream().anyMatch(p -> p.owner() != player
                && Math.abs(p.x() - self.x()) < 230 && Math.abs(p.y() - self.y() - 75) < 100);
        danger|=view.ultimates().stream().filter(u->u.owner()!=player).anyMatch(u->u.kind()==FighterKind.NAIWA
                ?u.strikes().stream().anyMatch(b->b.aimed()&&b.age()<0&&Math.abs(b.x()-self.x())<130)
                :u.age()>=FightSpecial.WEB_AIM&&u.age()<FightSpecial.WEB_CAPTURE&&Math.abs(u.targetX()-self.x())<160);
        boolean guard = danger && self.energy() > 14 && time % 90 < 66
                || distance < 150 && target.action() == FightSnapshot.Action.PUNCH && time % 3 != 0;
        double goal = target.x();
        if (target.y() > self.y() + 100 && self.grounded()) {
            double best = Double.MAX_VALUE;
            for (var plate : view.map().platforms()) {
                if (plate.height() > self.y() + 10 && plate.height() <= self.y() + 145) {
                    double center = (plate.left() + plate.right()) / 2;
                    double score = Math.abs(center - self.x()) + .25 * Math.abs(center - target.x());
                    if (score < best) { best = score; goal = center; }
                }
            }
        }
        int move = Math.abs(goal - self.x()) > 100 ? (goal > self.x() ? 1 : -1) : 0;
        boolean climb = target.y() > self.y() + 60 && Math.abs(goal - self.x()) < 300;
        boolean drop = self.grounded() && self.platform() >= 0 && target.y() < self.y() - 60 && distance < 380;
        boolean jump = !guard && (drop || climb && (self.grounded() || self.jumpCount() == 1 && self.vy() < 3) && time % 12 == 0
                || time % 210 == 0 || danger && self.energy() < 14 && self.jumpCount() < 2);
        boolean punch = distance < 135 && Math.abs(target.y() - self.y()) < 170 && time % 23 == 0;
        jump |= punch && target.y() > self.y() + 40 && self.grounded();
        boolean blink=!drop&&guard&&self.action()==FightSnapshot.Action.GUARD&&FightSpecial.blinkAffordable(self)&&time%31==0;
        if(blink){return new FightCommand(0,true,false,false,false,true,false,true);}
        return new FightCommand(guard ? 0 : move, jump, punch,
                distance > 180 && Math.abs(target.y() - self.y()) < 90 && time % 65 == 0,
                self.energy() >= 100 && Math.abs(target.y() - self.y()) < 100 && distance < 900,
                guard && !drop, distance > 480 && time % 120 == 0, drop);
    }
}
