package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FighterKind;
import dev.gamecore.fight.FightSnapshot.Action;

/** 以用户原图角色制作的透明 3D 质感动作精灵；只借用窗口资源，镜像不改写共享纹理。 */
public final class FightArt {
    private final ThemeManager theme;
    private final TextureRegion[] right = new TextureRegion[12];
    private final TextureRegion[] left = new TextureRegion[12];
    private TextureRegion[] motionRight, motionLeft;
    private float[] pivots;
    private TextureRegion[] boxingRight, boxingLeft;
    private float[] boxingPivots;
    private final float[] boxingScale = {1, 1};
    private TextureRegion[] greetingRight, greetingLeft;
    private float[] greetingPivots;
    private final float[] greetingScale = {1, 1};
    private TextureRegion[] victoryRight,victoryLeft;
    private float[] victoryPivots;
    private final float[] victoryScale={1,1};
    private TextureRegion[] laughRight,laughLeft;
    private float[] laughPivots;
    private float laughScale=1;
    public FightArt(ThemeManager theme) {
        this.theme = theme;
        for (int i = 0; i < right.length; i++) {
            var frame = FightSpriteLayout.frame(i);
            right[i] = new TextureRegion(theme.assets.fighters, frame.x(), frame.y(), frame.width(), frame.height());
            left[i] = new TextureRegion(right[i]); left[i].flip(true, false);
        }
        loadMotion();
        loadBoxing();
        loadGreetings();
        loadVictory();
        loadLaugh();
    }
    /** 奶蛙专用八笑姿，脚底/躯干按实测锚点对齐；透明原图不改像素，资源仍由窗口缓存拥有。 */
    private void loadLaugh(){
        try{
            var clips=FightSpriteLayout.readLaugh(com.badlogic.gdx.Gdx.files.internal("fighters/fighters-laugh-v1.json").readString("UTF-8"));
            var r=new TextureRegion[8];var l=new TextureRegion[8];var pivots=new float[8];
            for(var c:clips){int i=c.pose();r[i]=new TextureRegion(theme.assets.fighterSheet(c.sheet()),c.x(),c.y(),c.width(),c.height());l[i]=new TextureRegion(r[i]);l[i].flip(true,false);pivots[i]=c.pivotX();}
            laughScale=(motionRight!=null?motionRight[0].getRegionHeight():right[0].getRegionHeight())/(float)r[0].getRegionHeight();
            laughRight=r;laughLeft=l;laughPivots=pivots;
        }catch(RuntimeException failure){com.badlogic.gdx.Gdx.app.error("Fight","Laugh atlas unavailable; using existing pose",failure);}
    }
    public boolean laughLoaded(){return laughRight!=null;}
    /** 功夫庆祝使用专门姿态；两侧甩拳用朝向变化，旋身帧保留背面，不改真实朝向或位置。 */
    private void loadVictory(){
        try{
            var clips=FightSpriteLayout.readVictory(com.badlogic.gdx.Gdx.files.internal("fighters/fighters-victory-v1.json").readString("UTF-8"));
            TextureRegion[] r=new TextureRegion[16],l=new TextureRegion[16];float[] anchors=new float[16];
            for(var c:clips){int i=c.kind().ordinal()*8+c.pose();r[i]=new TextureRegion(theme.assets.fighterSheet(c.sheet()),c.x(),c.y(),c.width(),c.height());l[i]=new TextureRegion(r[i]);l[i].flip(true,false);anchors[i]=c.pivotX();}
            for(var kind:FighterKind.values()){int i=kind.ordinal()*8;victoryScale[kind.ordinal()]=(motionRight!=null?motionRight[i].getRegionHeight():right[kind.ordinal()*6].getRegionHeight())/(float)r[i].getRegionHeight();}
            victoryRight=r;victoryLeft=l;victoryPivots=anchors;
        }catch(RuntimeException failure){com.badlogic.gdx.Gdx.app.error("Fight","Victory atlas unavailable; using existing win pose",failure);}
    }
    public boolean victoryLoaded(){return victoryRight!=null;}
    public void victory(Batch batch,FighterKind kind,float x,float y,float unit,int facing,int pose,float angle){
        if(victoryRight==null){fighter(batch,kind,x,y,unit/.62f,facing,Action.WIN,0);return;}
        if(pose==2){facing=-facing;}
        int index=kind.ordinal()*8+pose;var region=facing>0?victoryRight[index]:victoryLeft[index];
        float pivot=facing>0?victoryPivots[index]:region.getRegionWidth()-victoryPivots[index];unit*=victoryScale[kind.ordinal()];
        batch.setColor(Color.WHITE);batch.draw(region,x-pivot*unit,y,pivot*unit,0,region.getRegionWidth()*unit,region.getRegionHeight()*unit,1,1,angle);
    }
    /** 问候素材独立于战斗动作，镜头演出不伪造规则快照或触发伤害。 */
    private void loadGreetings() {
        try {
            var clips = FightSpriteLayout.readGreetings(com.badlogic.gdx.Gdx.files.internal("fighters/fighters-greetings-v1.json").readString("UTF-8"));
            TextureRegion[] r = new TextureRegion[16], l = new TextureRegion[16]; float[] anchors = new float[16];
            for (var c : clips) {
                int i = c.kind().ordinal() * 8 + c.pose();
                r[i] = new TextureRegion(theme.assets.fighterSheet(c.sheet()), c.x(), c.y(), c.width(), c.height());
                l[i] = new TextureRegion(r[i]); l[i].flip(true, false); anchors[i] = c.pivotX();
            }
            for (var kind : FighterKind.values()) {
                int i = kind.ordinal() * 8;
                greetingScale[kind.ordinal()] = (motionRight != null ? motionRight[i].getRegionHeight() : right[kind.ordinal() * 6].getRegionHeight()) / (float) r[i].getRegionHeight();
            }
            greetingRight = r; greetingLeft = l; greetingPivots = anchors;
        } catch (RuntimeException failure) { com.badlogic.gdx.Gdx.app.error("Fight", "Greeting atlas unavailable; using existing pose", failure); }
    }
    public boolean greetingsLoaded() { return greetingRight != null; }
    public void greeting(Batch batch, FighterKind kind, float x, float y, float unit, int facing, int pose) {
        if (greetingRight == null) { fighter(batch, kind, x, y, unit / .62f, facing, Action.WIN, 0); return; }
        int index = kind.ordinal() * 8 + pose; var region = facing > 0 ? greetingRight[index] : greetingLeft[index];
        float pivot = facing > 0 ? greetingPivots[index] : region.getRegionWidth() - greetingPivots[index]; unit *= greetingScale[kind.ordinal()];
        batch.setColor(Color.WHITE); batch.draw(region, x - pivot * unit, y, region.getRegionWidth() * unit, region.getRegionHeight() * unit);
    }
    /** 同一角色的新拳架与既有站姿对齐尺度；纹理继续由窗口资源管理器缓存/释放。 */
    private void loadBoxing() {
        try {
            var clips = FightSpriteLayout.readBoxing(com.badlogic.gdx.Gdx.files.internal("fighters/fighters-boxing-v1.json").readString("UTF-8"));
            TextureRegion[] r = new TextureRegion[16], l = new TextureRegion[16]; float[] anchors = new float[16];
            for (var clip : clips) {
                int i = clip.kind().ordinal() * 8 + clip.pose();
                r[i] = new TextureRegion(theme.assets.fighterSheet(clip.sheet()), clip.x(), clip.y(), clip.width(), clip.height());
                l[i] = new TextureRegion(r[i]); l[i].flip(true, false); anchors[i] = clip.pivotX();
            }
            for (var kind : FighterKind.values()) {
                int i = kind.ordinal() * 8;
                float reference = motionRight != null ? motionRight[i].getRegionHeight() : right[kind.ordinal() * 6].getRegionHeight();
                boxingScale[kind.ordinal()] = reference / r[i].getRegionHeight();
            }
            boxingRight = r; boxingLeft = l; boxingPivots = anchors;
        } catch (RuntimeException failure) { com.badlogic.gdx.Gdx.app.error("Fight", "Boxing atlas unavailable; using existing combat poses", failure); }
    }
    public boolean boxingLoaded() { return boxingRight != null; }
    /** 读入经过实测的区域；任何表/资源错误回落旧图集，页面仍可操作。 */
    private void loadMotion() {
        try {
            var clips = FightSpriteLayout.read(com.badlogic.gdx.Gdx.files.internal("fighters/fighters-v3.json").readString("UTF-8"));
            TextureRegion[] r = new TextureRegion[48], l = new TextureRegion[48]; float[] anchors = new float[48];
            for (var clip : clips) {
                int group = switch (clip.group()) { case "locomotion" -> 0; case "combat" -> 1; case "air-states" -> 2;
                    default -> throw new IllegalArgumentException("Unknown sprite group"); };
                int i = group * 16 + clip.kind().ordinal() * 8 + clip.pose();
                r[i] = new TextureRegion(theme.assets.fighterSheet(clip.sheet()), clip.x(), clip.y(), clip.width(), clip.height());
                l[i] = new TextureRegion(r[i]); l[i].flip(true, false); anchors[i] = clip.pivotX();
            }
            for (var region : r) { if (region == null) { throw new IllegalArgumentException("Missing pose"); } }
            motionRight = r; motionLeft = l; pivots = anchors;
        } catch (RuntimeException failure) { com.badlogic.gdx.Gdx.app.error("Fight", "Motion atlas unavailable; using original character sprites", failure); }
    }
    public void fighter(Batch batch, FighterKind kind, float x, float y, float scale, int facing, Action action, float phase) {
        render(batch, kind, x, y, .62f * scale, facing, action, (int) (phase * 60), phase * 80, 1, 0);
    }
    /** 动画行走相位来自累计位移，静止时不空转；腾空/落地按规则动作做挤压拉伸。 */
    public void fighter(Batch batch, dev.gamecore.fight.FightSnapshot.Fighter f, float x, float y, float unit, float alpha) {
        fighter(batch,f,x,y,unit,alpha,0);
    }
    /** 受击暖色短闪只影响当前绘制，不改动作、脚底、判定或透明区域。 */
    public void fighter(Batch batch,dev.gamecore.fight.FightSnapshot.Fighter f,float x,float y,float unit,float alpha,float flash){
        render(batch,f.kind(),x,y,unit,f.facing(),f.action(),f.poseTick(),f.travel(),alpha,Math.max(0,Math.min(1,flash)));
    }
    private void render(Batch batch, FighterKind kind, float x, float y, float unit, int facing, Action action, int poseTick, double travel, float alpha,float flash) {
        if(kind==FighterKind.NAIWA&&action==Action.LAUGH&&laughRight!=null){
            int pose=FightLaughMotion.pose(poseTick);var r=facing>0?laughRight[pose]:laughLeft[pose];float pivot=facing>0?laughPivots[pose]:r.getRegionWidth()-laughPivots[pose];
            unit*=laughScale;float squash=FightLaughMotion.squash(poseTick);batch.setColor(1,1,1,alpha);
            batch.draw(r,x-pivot*unit,y,pivot*unit,0,r.getRegionWidth()*unit,r.getRegionHeight()*unit,1/squash,squash,FightLaughMotion.angle(poseTick)*facing);return;
        }
        int group = 0, pose = 0;
        switch (action) {
            case IDLE -> { }
            case WALK -> pose = 1 + (int) (travel / 30) % 2;
            case RUN -> pose = 3 + (int) (travel / 28) % 3;
            case SKID -> pose = 6;
            case DASH -> pose = 7;
            case PUNCH -> { group = 1; pose = 0; }
            case PUNCH_2 -> { group = 1; pose = 1; }
            case PUNCH_3, UPPERCUT -> { group = 1; pose = 2; }
            case AIR_PUNCH -> { group = 1; pose = 3; }
            case RANGED -> { group = 1; pose = 4; }
            case ULTIMATE -> { group = 1; pose = poseTick<12?4:5; }
            case ULT_RELEASE -> { group = 1; pose = 6; }
            case GUARD, GUARD_HIT -> { group = 1; pose = 7; }
            case HURT -> { group = 2; pose = 0; }
            case HURT_HEAVY -> { group = 2; pose = 1; }
            case JUMP_UP -> { group = 2; pose = 2; }
            case JUMP_APEX, JUMP_FALL -> { group = 2; pose = 3; }
            case DOUBLE_JUMP -> { group = 2; pose = 4; }
            case KNOCKDOWN, LOSE -> { group = 2; pose = 5; }
            case GETUP, LAND -> { group = 2; pose = 6; }
            case WIN -> { group = 2; pose = 7; }
            case LAUGH -> { group = 2;pose = 7; }
            case WEB_BOUND -> {group=2;pose=0;}
            case TELEPORT -> {group=1;pose=6;}
        }
        int index = group * 16 + kind.ordinal() * 8 + pose;
        TextureRegion region; float pivot;
        int boxingPose = FightBoxingMotion.pose(action, poseTick);
        if (boxingRight != null && boxingPose >= 0) {
            index = kind.ordinal() * 8 + boxingPose;
            region = facing > 0 ? boxingRight[index] : boxingLeft[index]; pivot = boxingPivots[index];
            unit *= boxingScale[kind.ordinal()];
        } else if (motionRight != null) { region = facing > 0 ? motionRight[index] : motionLeft[index]; pivot = pivots[index]; }
        else { index = FightSpriteLayout.index(kind, action); region = facing > 0 ? right[index] : left[index]; pivot = FightSpriteLayout.frame(index).pivotX(); }
        if (facing < 0) { pivot = region.getRegionWidth() - pivot; }
        float phase = (float) (travel / (action == Action.RUN ? 56 : 60) * Math.PI);
        float squash = action == Action.LAND ? .87f : action == Action.JUMP_UP ? 1.05f : action == Action.RUN ? 1 + .025f * (float) Math.sin(phase) : 1;
        float bob = action == Action.RUN || action == Action.WALK ? Math.abs((float) Math.sin(phase)) * 4 * unit : 0;
        float angle = action == Action.DASH ? -facing * 8 : action == Action.SKID ? facing * 7 : 0;
        if(action==Action.ULTIMATE){squash=1+.025f*(float)Math.sin(poseTick*.22);angle=-facing*3;}
        else if(action==Action.ULT_RELEASE){angle=-facing*(3+3*(float)Math.sin(poseTick*.12));}
        else if(action==Action.WEB_BOUND){squash=.94f;angle=(float)Math.sin(poseTick*.3)*3;}
        else if(action==Action.TELEPORT){angle=-facing*9;squash=1.04f;}
        if (boxingPose >= 0) {
            float thrust = FightBoxingMotion.thrust(action, poseTick);
            int style = dev.gamecore.fight.FightPunch.from(action).ordinal();
            angle = -facing * thrust * (style == 0 ? 2 : style == 1 ? 7 : 11);
            x += facing * thrust * (style == 0 ? 12 : 20) * unit;
            squash = 1 - Math.max(0, thrust) * .025f;
        }
        batch.setColor(1, 1-flash*.62f, 1-flash*.68f, alpha);
        batch.draw(region, x - pivot * unit, y + bob, pivot * unit, 0,
                region.getRegionWidth() * unit, region.getRegionHeight() * unit, 1 / squash, squash, angle);
    }
    public void oval(Batch batch, Color color, float x, float y, float width, float height, float angle) {
        batch.setColor(color);
        batch.draw(theme.assets.circle, x - width / 2, y - height / 2, width / 2, height / 2,
                width, height, 1, 1, angle, 0, 0, 64, 64, false, false);
    }
    public void line(Batch batch, Color color, float x1, float y1, float x2, float y2, float width) {
        float dx = x2 - x1, dy = y2 - y1, length = (float) Math.sqrt(dx * dx + dy * dy);
        batch.setColor(color);
        batch.draw(theme.assets.pixel, x1, y1 - width / 2, 0, width / 2, length, width, 1, 1,
                (float) Math.toDegrees(Math.atan2(dy, dx)), 0, 0, 1, 1, false, false);
    }
}
