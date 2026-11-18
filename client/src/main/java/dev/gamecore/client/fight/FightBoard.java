package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FightArena;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FighterKind;
import dev.gamecore.fight.FightMap;

/** 霓虹城市竞技场：同一镜头投影覆盖角色/平台/粒子；预分配绘图颜色与数字，不在 draw 中创建容器。 */
public final class FightBoard extends Widget {
    private static final Color SKY = Color.valueOf("070e20"), CITY = Color.valueOf("101f39"), FRONT = Color.valueOf("1b304b"),
            CYAN = Color.valueOf("59e8ff"), VIOLET = Color.valueOf("b77bff"), GREEN = Color.valueOf("b2ff9b"), GOLD = Color.valueOf("ffca6f"),
            STEEL = Color.valueOf("263c58"), WHITE = Color.valueOf("f7ffff"), SHADOW = Color.valueOf("02070dcc");
    private final ThemeManager theme;
    private final FightArt art;
    private final FightSceneryRenderer scenery;
    private final FightCamera camera = new FightCamera();
    private final FightVfx vfx = new FightVfx();
    private final FightImpactFeedback feedback=new FightImpactFeedback();
    private final FightImpactRenderer impactRenderer;
    private final FightSpecialRenderer specialRenderer;
    private final FightTypography typography;
    private final FightChrome mapBadge;
    private final FightOpeningOverlay openingOverlay;
    private final FightVictory victory=new FightVictory();
    private final FightVictoryOverlay victoryOverlay;
    private FightOpening opening;
    private FightOpening.Phase lastOpening = FightOpening.Phase.IDLE;
    private final com.badlogic.gdx.math.Rectangle clipBounds = new com.badlogic.gdx.math.Rectangle(), clipScissor = new com.badlogic.gdx.math.Rectangle();
    private final Color tint = new Color();
    private final String[] numbers = new String[101];
    private FightSnapshot view;
    private float time, flash, intro, finishAge;
    private boolean debug;
    private String resultText = "";
    private final String[] comboText = {"", ""};
    public FightBoard(ThemeManager theme) { this.theme = theme;mapBadge=new FightChrome(theme,CYAN); art = new FightArt(theme);scenery=new FightSceneryRenderer(theme,art);impactRenderer=new FightImpactRenderer(theme,art);specialRenderer=new FightSpecialRenderer(theme,art); typography = new FightTypography(theme); openingOverlay = new FightOpeningOverlay(theme, art);victoryOverlay=new FightVictoryOverlay(theme,art); for (int i = 0; i < numbers.length; i++) { numbers[i] = "-"+i; } }
    public void opening(FightOpening value) { opening = value; }
    public void restart() { vfx.clear();feedback.clear(); camera.reset();victory.reset(); finishAge = flash = intro = 0; lastOpening = FightOpening.Phase.IDLE; }
    public FightImpactFeedback feedback(){return feedback;}
    public FightSpecialView.Focus specialFocus(){return view==null?new FightSpecialView.Focus(-1,1):FightSpecialView.focus(view);}
    public float cameraZoom(){return camera.zoom();}
    public FightVictory victory(){return victory;}
    public boolean victoryLoaded(){return art.victoryLoaded();}
    public boolean laughLoaded(){return art.laughLoaded();}
    public boolean sceneryMaterialsLoaded(){return scenery.materialsLoaded();}
    public int laughFocus(){return view==null?-1:FightLaughMotion.focusedPlayer(view);}
    public boolean greetingsLoaded() { return art.greetingsLoaded(); }
    public void update(FightSnapshot next) {
        if (view != null && next.tick() < view.tick()) { vfx.clear();feedback.clear(); finishAge = flash = intro = 0; }
        view = next;
        mapBadge.skin(FightUiSkin.of(next.map()),0);
        victory.observe(next);victoryOverlay.update(next,victory);
        resultText = next.fighters().get(0).totalDamage() + " DAMAGE   /   " + next.fighters().get(1).totalDamage() + " DAMAGE";
        for (int p = 0; p < 2; p++) { comboText[p] = next.fighters().get(p).comboCount() + " HIT"; }
        vfx.ingest(next);
        if(feedback.ingest(next)){camera.impact(feedback.impulse());if(feedback.impulse()>=.6f){flash=.035f;}}

    }
    /** 在线每帧只更新连续坐标；命中/胜者/粒子仍由收到的权威快照更新一次。 */
    public void displayMotion(FightSnapshot next){view=next;}
    /** 暂停冻结全部演出；终局只完成有限的余效，规则始终已经停止。 */
    public void advance(float delta) {
        if (view == null || view.state() == SessionState.PAUSED || victory.paused() || opening != null && opening.active() && opening.paused()) { return; }
        victory.advance(delta);
        float dt = Math.min(Math.max(delta, 0), .1f);
        time += dt; flash = Math.max(0, flash - dt); intro = Math.max(0, intro - dt);
        if (opening != null && opening.phase() != lastOpening) {
            lastOpening = opening.phase();
            if (opening.number() > 0 || lastOpening == FightOpening.Phase.FIGHT) { camera.impact(.8f); }
        }
        int focus=victory.focusedPlayer()>=0?victory.focusedPlayer():opening!=null&&opening.focusedPlayer()>=0?opening.focusedPlayer():-1;
        vfx.update(dt);feedback.advance(dt);
        if(focus>=0||view.ultimates().isEmpty()&&view.teleports().isEmpty()){if(focus<0){focus=laughFocus();}camera.update(view,dt,focus,victory.focusedPlayer()>=0?victory.feetY():focus>=0?view.fighters().get(focus).y():0);}
        else{var special=FightSpecialView.focus(view);camera.special(view,dt,special.player(),special.player()>=0?view.fighters().get(special.player()).y():0,special.zoom());}
    }
    public int particles() { return vfx.count(); }
    public boolean boxingLoaded() { return art.boxingLoaded(); }
    public void debug(boolean enabled) { debug = enabled; }
    @Override public float getMinWidth() { return 0; }
    @Override public float getMinHeight() { return 0; }
    @Override public void draw(Batch batch, float parentAlpha) {
        if (getStage() == null) { paint(batch, parentAlpha); return; }
        batch.flush(); clipBounds.set(getX(), getY(), getWidth(), getHeight()); getStage().calculateScissors(clipBounds, clipScissor);
        if (!com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.pushScissors(clipScissor)) { return; }
        try { paint(batch, parentAlpha); }
        finally { batch.flush(); com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.popScissors(); }
    }
    private void paint(Batch batch, float parentAlpha) {
        if (view == null) { return; }
        float packed = batch.getPackedColor();
        camera.viewport(getX(), getY(), getWidth(), getHeight());
        fill(batch, SKY, 0, 0, getWidth(), getHeight(), 1);
        var style=FightMapStyle.of(view.map());var stageArt = theme.assets.fightStage(view.map());
        if (stageArt != null) { batch.setColor(.88f, .88f, 1, 1); batch.draw(stageArt, getX(), getY(), getWidth(), getHeight()); fill(batch, SKY, 0, 0, getWidth(), getHeight(), .22f); }
        float s = camera.scale(), ox = camera.ox(), oy = camera.oy();
        for (int i = 9; i > 0; i--) {
            art.oval(batch, color(style.primary, .014f), ox + 300 * s, oy + 400 * s, i * 150 * s, i * 130 * s, 0);
            art.oval(batch, color(style.secondary, .014f), ox + 1600 * s, oy + 430 * s, i * 160 * s, i * 140 * s, 0);
        }
        // 三层天际线与吊缆，前后景使用不同视差，不改变世界碰撞。
        if(view.map()==FightMap.NEON){for (int i = -2; i < 50; i++) {
            float x = ox + (i * 47 - (camera.center() - 1120) * .20f) * s;
            float height = 80 + Math.floorMod(i * 37, 140);
            batch.setColor(color(CITY, stageArt == null ? 1 : .18f)); batch.draw(theme.assets.pixel, x, oy + 76 * s, 36 * s, height * s);
            for (int j = 0; j < 4; j++) {
                batch.setColor(color(i % 3 == 0 ? VIOLET : CYAN, .25f));
                batch.draw(theme.assets.pixel, x + 7 * s, oy + (90 + j * 24) * s, 5 * s, 7 * s);
            }
            if (i % 4 == 0) { art.line(batch, color(CYAN, .15f), x, oy + (height + 100) * s, x + 180 * s, oy + 600 * s, s); }
        }}
        worldRect(batch, style.ground, 0, -76, 2240, 76, stageArt == null ? 1 : .3f);
        for (int i = 0; i < 45; i++) { worldLine(batch, color(style.primary, .15f), i * 80, 0, 1120 + (i * 80 - 1120) * 1.4f, -76, 1); }
        for (int i = 0; i < 4; i++) { worldLine(batch, color(style.primary, .16f), 0, -i * 24, 2240, -i * 24, 1); }
        worldRect(batch, style.primary, 0, -3, 1120, 3, .9f); worldRect(batch, style.secondary, 1120, -3, 1120, 3, .9f);
        atmosphere(batch,style);
        if(view.map()==FightMap.NEON){for (int i = 0; i < 40; i++) {
            float x = Math.floorMod(i * 179, 2240), y = (i * 71 + time * 12) % 650;
            art.oval(batch, color(i % 2 == 0 ? CYAN : VIOLET, .25f), camera.x(x), camera.y(y), 2 * s, 2 * s, 0);
        }
        // 竞技场中心的能量反应环和两端光柱属于视差布景，不构成可碰撞物体。
        ring(batch, CYAN, 1120, 330, 215, 1, .12f); ring(batch, VIOLET, 1120, 330, 195, 2, .10f);
        for (int i = 0; i < 12; i++) {
            double a = time * .12 + i * Math.PI / 6;
            worldLine(batch, color(i % 2 == 0 ? CYAN : VIOLET, .16f), 1120 + Math.cos(a) * 165, 330 + Math.sin(a) * 165,
                    1120 + Math.cos(a) * 185, 330 + Math.sin(a) * 185, 3);
        }
        for (int side = 0; side < 2; side++) {
            Color c = side == 0 ? CYAN : VIOLET; double x = side == 0 ? 60 : 2180;
            worldLine(batch, color(c, .07f), x, 0, x, 510, 18); worldLine(batch, color(c, .6f), x, 0, x, 510, 2);
            for (int i = 0; i < 13; i++) { worldLine(batch, color(c, .45f), x - 8, i * 36 + time * 12 % 36, x + 8, i * 36 + time * 12 % 36, 1); }
        }
        }
        if ((opening == null || opening.focusedPlayer() < 0)&&victory.focusedPlayer()<0) {
            scenery.draw(batch,view.map(),camera,time,1);
            if(debug){for(var plate:view.map().platforms()){worldLine(batch,GREEN,plate.left(),plate.height(),plate.right(),plate.height(),2);}}
        }
        specialRenderer.background(batch,view,camera,getX(),getY(),getWidth(),getHeight());
        for (var f : view.fighters()) {
            if(victory.focusedPlayer()>=0){if(f.player()!=victory.winner()){continue;}
                ring(batch,GOLD,f.x(),victory.feetY()+5,85,3,.7f);
                art.victory(batch,f.kind(),camera.x(f.x()+victory.xOffset()),camera.y(victory.feetY()+victory.bob()),.34f*camera.unit(),f.facing(),victory.pose(),victory.angle());continue;}
            Color element = f.kind() == FighterKind.NAIWA ? CYAN : VIOLET;
            art.oval(batch, color(element, .18f), camera.x(f.x()), camera.y(f.y()) - 2 * s, 100 * camera.unit(), 10 * camera.unit(), 0);
            if (f.action() == FightSnapshot.Action.RUN || f.action() == FightSnapshot.Action.DASH) {
                for (int i = 4; i > 0; i--) { art.fighter(batch, f, camera.x(f.x() - f.facing() * i * 19), camera.y(f.y()), .34f * camera.unit(), .035f * (5 - i)); }
            }
            if (f.action() == FightSnapshot.Action.ULTIMATE) {
                chargeAura(batch, f, element);
                ring(batch, element, f.x(), f.y() + 50, 55 + 8 * Math.sin(time * 40), 2, 1);
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4 + time * 4; worldLine(batch, color(element, .6f), f.x() + Math.cos(a) * 90, f.y() + 50 + Math.sin(a) * 90, f.x() + Math.cos(a) * 48, f.y() + 50 + Math.sin(a) * 48, 3); }
            }
            boxingTrail(batch, f, element);
            int greeting = opening != null ? opening.greetingPose(f.player()) : -1;
            if (greeting >= 0) { art.greeting(batch, f.kind(), camera.x(f.x()), camera.y(f.y()), .34f * camera.unit(), f.facing(), greeting); }
            else { art.fighter(batch, f, camera.x(f.x()), camera.y(f.y()), .34f * camera.unit(), 1,feedback.hurtFlash(f.player())); }
            if(f.action()==FightSnapshot.Action.LAUGH){
                float sway=(float)Math.sin(f.poseTick()*.3);
                typography.draw(batch,"HA!",camera.x(f.x()-80),camera.y(f.y()+136+sway*5),23*camera.unit(),90*camera.unit(),GOLD,1);
                typography.draw(batch,"HAHA!",camera.x(f.x()+95),camera.y(f.y()+105-sway*5),16*camera.unit(),105*camera.unit(),CYAN,1);
            }
            if (f.action() == FightSnapshot.Action.GUARD || f.action() == FightSnapshot.Action.GUARD_HIT) {
                ring(batch, CYAN, f.x() + f.facing() * 14, f.y() + 52, 63, 3, .8f);
                ring(batch, WHITE, f.x() + f.facing() * 14, f.y() + 52, 57, 1, .5f);
            }
            if (f.slowTicks() > 0) { for (int i = 0; i < 3; i++) { ring(batch, VIOLET, f.x(), f.y() + 25 + i * 20, 41, 1, .55f); } }
            if (debug) { box(batch, GREEN, f.x() - 35, f.y(), 70, 100); }
        }
        if(victory.focusedPlayer()<0){for (var shot : view.projectiles()) { projectile(batch, shot); }specialRenderer.front(batch,view,camera);impactRenderer.draw(batch,feedback,camera);}
        for (int i = 0; i < FightVfx.CAPACITY; i++) {
            if (vfx.life[i] <= 0) { continue; }
            float t = 1 - vfx.life[i] / vfx.duration[i];
            Color c = vfx.element[i] == 0 ? CYAN : vfx.element[i] == 1 ? VIOLET : GOLD;
            switch (vfx.type[i]) {
                case FightVfx.SPARK -> {
                    worldLine(batch, color(c, 1 - t), vfx.x[i], vfx.y[i], vfx.x[i] - vfx.vx[i] * .025, vfx.y[i] - vfx.vy[i] * .025, 5 * (1 - t) + 1);
                    worldLine(batch, color(WHITE, 1 - t), vfx.x[i], vfx.y[i], vfx.x[i] - vfx.vx[i] * .006, vfx.y[i] - vfx.vy[i] * .006, 2);
                }
                case FightVfx.RING -> { ring(batch, c, vfx.x[i], vfx.y[i], 10 + t * 105, 4 * (1 - t) + 1, 1 - t); ring(batch, WHITE, vfx.x[i], vfx.y[i], 5 + t * 65, 2, 1 - t); }
                case FightVfx.BURST -> art.oval(batch,color(WHITE,(1-t)*(1-t)),camera.x(vfx.x[i]),camera.y(vfx.y[i]),(28+12*t)*camera.unit(),(24-20*t)*camera.unit(),-20*vfx.vx[i]);
                case FightVfx.NUMBER -> typography.draw(batch,numbers[Math.min(100,vfx.value[i])],camera.x(vfx.x[i]),camera.y(vfx.y[i]),27*camera.unit()*(1+Math.max(0,.15f-t)),120*camera.unit(),c,1-t*.75f);
                case FightVfx.DUST -> art.oval(batch, color(c, .3f * (1 - t)), camera.x(vfx.x[i]), camera.y(vfx.y[i]), (10 + t * 30) * camera.unit(), (5 + t * 15) * camera.unit(), 0);
                default -> throw new IllegalStateException("Unknown visual particle");
            }
        }
        // 装饰层只作用画面；轻扫描线、四角括号和两端暗角保留战斗信息的可读性。
        for (float y = 0; y < getHeight(); y += 4) { fill(batch, SHADOW, 0, y, getWidth(), 1, .12f); }
        for (int i = 0; i < 10; i++) { fill(batch, SKY, i * 5, 0, 5, getHeight(), .5f - i * .045f); fill(batch, SKY, getWidth() - (i + 1) * 5, 0, 5, getHeight(), .5f - i * .045f); }
        if (flash > 0) { fill(batch, WHITE, 0, 0, getWidth(), getHeight(), flash * 2); }
        if((opening==null||!opening.active())&&view.state()!=SessionState.FINISHED&&laughFocus()<0){
            for(var f:view.fighters()){if(f.comboCount()<2){continue;}float cx=getX()+getWidth()*(f.player()==0?.11f:.89f),top=getY()+getHeight()*.83f;
                typography.draw(batch,comboText[f.player()],cx,top,49*s,getWidth()*.20f,GOLD,1);
                typography.draw(batch,"HIT COMBO",cx,top-60*s,13*s,getWidth()*.18f,f.player()==0?CYAN:VIOLET,1);
            }
        }
        if (view.state() == SessionState.FINISHED && victory.focusedPlayer()<0) {
            fill(batch, SKY, 0, getHeight() * .40f, getWidth(), getHeight() * .29f, .90f);
            typography.draw(batch, view.result() == FightSnapshot.Result.DRAW ? "DRAW" : "K.O.", getX() + getWidth() / 2, oy + 470 * s, 92 * s, getWidth() * .8f, GOLD, 1);
            text(batch, resultText, ox + 755 * s, oy + 340 * s, 1.0f * s, WHITE);
        }
        if(victory.focusedPlayer()>=0){victoryOverlay.draw(batch,view,victory,getX(),getY(),getWidth(),getHeight());}
        if((opening==null||!opening.active())&&view.state()!=SessionState.FINISHED){
            float bx=getX()+15,by=getY()+getHeight()-58;
            mapBadge.draw(batch,bx,by,220,50);
            typography.draw(batch,view.map().toString(),bx+110,by+40,18,190,style.primary,1);
            typography.draw(batch,FightUiSkin.of(view.map()).subtitle,bx+110,by+12,8,190,style.secondary,1);
            if(view.state()==SessionState.READY){typography.draw(batch,view.map().feature(),getX()+getWidth()*.5f,getY()+getHeight()*.89f,19*s,getWidth()*.85f,style.secondary,1);}
        }
        specialRenderer.overlay(batch,view,camera,getX(),getY(),getWidth(),getHeight());
        if (opening != null) { openingOverlay.draw(batch, opening, view, getX(), getY(), getWidth(), getHeight()); }
        if(laughFocus()>=0&&view.ultimates().isEmpty()&&view.teleports().isEmpty()){typography.draw(batch,"奶蛙 · 捧腹大笑",getX()+getWidth()*.5f,getY()+getHeight()*.94f,29*s,getWidth()*.62f,GOLD,1);}
        batch.setPackedColor(packed);
    }
    /** 地图气氛不参与碰撞：武馆飘花，铸场余烬/热浪；相同角色技能仍保留自己的元素色。 */
    private void atmosphere(Batch batch,FightMapStyle style){
        if(view.map()==FightMap.TEMPLE){for(int i=0;i<36;i++){double x=(i*137+time*34)%2240,y=(i*79-time*17)%640;if(y<0){y+=640;}
            art.oval(batch,color(style.secondary,.55f),camera.x(x+Math.sin(time+i)*20),camera.y(y),12*camera.unit(),5*camera.unit(),time*35+i*29);}}
        else if(view.map()==FightMap.FORGE){for(int i=0;i<40;i++){double x=(i*173+Math.sin(time+i)*18)%2240,y=(i*67+time*(35+i%5*12))%590;
            worldLine(batch,color(i%4==0?GOLD:style.primary,.6f),x,y,x-3,y-10-i%7,2);}
            for(int i=0;i<3;i++){double x=350+i*770;art.oval(batch,color(style.primary,.05f),camera.x(x),camera.y(24),180*camera.unit(),(80+20*(float)Math.sin(time*2+i))*camera.unit(),0);}}
        else if(view.map()==FightMap.BAMBOO){for(int i=0;i<25;i++){double x=(i*137+time*18+Math.sin(time+i)*23)%2240,y=(i*83-time*16)%640;if(y<0){y+=640;}
            art.oval(batch,color(style.primary,.55f),camera.x(x),camera.y(y),11*camera.unit(),3*camera.unit(),time*24+i*17);
            art.oval(batch,color(style.secondary,.18f+.15f*(float)Math.sin(time*2+i)),camera.x((i*193+time*9)%2240),camera.y(60+i*19%470),3*camera.unit(),3*camera.unit(),0);}}
        else if(view.map()==FightMap.FROST){for(int i=0;i<52;i++){double x=(i*157+time*12+Math.sin(time+i)*15)%2240,y=(i*71-time*(14+i%4*7))%640;if(y<0){y+=640;}
            art.oval(batch,color(WHITE,.25f+i%3*.13f),camera.x(x),camera.y(y),(3+i%3)*camera.unit(),(3+i%3)*camera.unit(),0);}}
    }
    /** 短促拳风区别直线刺拳、斜向后手和弧线摆拳；打空也播放，绝不增加伤害或判定范围。 */
    private void boxingTrail(Batch batch, FightSnapshot.Fighter fighter, Color element) {
        var punch = dev.gamecore.fight.FightPunch.from(fighter.action());
        if (punch == null || fighter.poseTick() < punch.strikeTick() - 1 || fighter.poseTick() > punch.strikeTick() + 3) { return; }
        float alpha = .65f - .10f * (fighter.poseTick() - punch.strikeTick() + 1);
        double x = fighter.x(), y = fighter.y(), d = fighter.facing();
        if (punch == dev.gamecore.fight.FightPunch.HOOK) {
            for (int i = 0; i < 12; i++) {
                double a = -.9 + i * .15, b = a + .15;
                worldLine(batch, color(element, alpha * (i + 1) / 12), x + d * (35 + Math.cos(a) * 85), y + 65 + Math.sin(a) * 32,
                        x + d * (35 + Math.cos(b) * 85), y + 65 + Math.sin(b) * 32, 3);
            }
        } else {
            double startY = y + (punch == dev.gamecore.fight.FightPunch.JAB ? 60 : 38);
            worldLine(batch, color(element, alpha), x + d * 30, startY, x + d * 128, y + 65, 4);
            worldLine(batch, color(WHITE, alpha), x + d * 56, startY + 4, x + d * 120, y + 67, 1);
        }
    }
    private void projectile(Batch batch, FightSnapshot.Projectile p) {
        double x = p.x(), y = p.y(); float size = p.ultimate() ? 2.8f : 1;
        Color c = p.kind() == FighterKind.NAIWA ? CYAN : VIOLET;
        for (int i = 5; i > 0; i--) { art.oval(batch, color(c, .025f * (6 - i)), camera.x(x - p.direction() * i * 12), camera.y(y), (70 + i * 20) * size * camera.unit(), (25 + i * 9) * size * camera.unit(), 0); }
        if (p.kind() == FighterKind.NAIWA) {
            for (int glow = 5; glow > 0; glow--) { art.oval(batch, color(CYAN, .045f * (6 - glow)), camera.x(x + p.direction() * 32), camera.y(y), (25 + glow * 12) * size * camera.unit(), (22 + glow * 10) * size * camera.unit(), 0); }
            art.oval(batch, WHITE, camera.x(x + p.direction() * 37), camera.y(y), 20 * size * camera.unit(), 15 * size * camera.unit(), 0);
            worldLine(batch, color(CYAN, .7f), x, y - 26 * size, x + p.direction() * 70 * size, y, 4 * size);
            worldLine(batch, WHITE, x + p.direction() * 70 * size, y, x, y + 26 * size, 2 * size);
            for (int branch = 0; branch < 3; branch++) {
                double px = x - p.direction() * 100 * size, py = y;
                for (int i = 0; i < 7; i++) {
                    double nx = px + p.direction() * 22 * size, ny = y + Math.sin((view.tick() / 2 + i * 13 + branch * 7) * 1.7) * 22 * size;
                    worldLine(batch, color(CYAN, .28f), px, py, nx, ny, 13 * size);
                    worldLine(batch, WHITE, px, py, nx, ny, 2 * size); px = nx; py = ny;
                }
            }
            if (p.ultimate()) { for (int i = -1; i <= 1; i++) { bolt(batch, x + i * 100, y, 650, view.tick() + i); } ring(batch, CYAN, x, y - 50, 125, 5, .7f); }
        } else {
            for (int glow = 5; glow > 0; glow--) { art.oval(batch, color(VIOLET, .04f * (6 - glow)), camera.x(x), camera.y(y), (65 + glow * 18) * size * camera.unit(), (65 + glow * 18) * size * camera.unit(), 0); }
            for (int strand = 0; strand < 8; strand++) {
                double px = x - p.direction() * 110, py = y;
                for (int i = 1; i <= 9; i++) {
                    double nx = x - p.direction() * (110 - i * 14), ny = y + Math.sin(i * .6 + strand * .8 + view.tick() * .12) * (10 + strand * 2) * size;
                    worldLine(batch, color(VIOLET, .6f), px, py, nx, ny, 3); px = nx; py = ny;
                }
            }
            double r = 28 * size;
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6 + time, b = a + Math.PI / 6;
                worldLine(batch, GREEN, x, y, x + Math.cos(a) * r, y + Math.sin(a) * r, 1.5f);
                for (int j = 1; j <= 3; j++) { double rr = r * j / 3; worldLine(batch, color(VIOLET, .9f), x + Math.cos(a) * rr, y + Math.sin(a) * rr, x + Math.cos(b) * rr, y + Math.sin(b) * rr, 2); }
            }
            art.oval(batch, color(GREEN, .8f), camera.x(x), camera.y(y), 12 * size * camera.unit(), 12 * size * camera.unit(), 0);
            ring(batch, GREEN, x, y, 34 * size, 2 * size, .65f);
        }
    }
    /** 蓄力的收束轨道/电弧与升腾碎片，体现角色元素；粒子位置从视觉时间计算，无规则实体。 */
    private void chargeAura(Batch batch, FightSnapshot.Fighter f, Color element) {
        for (int layer = 0; layer < 3; layer++) {
            double radius = 70 + layer * 22, rotation = time * (layer % 2 == 0 ? 2.2 : -1.6);
            for (int i = 0; i < 24; i++) { double a = rotation + i * Math.PI / 12, b = a + Math.PI / 12;
                worldLine(batch, color(element, .75f - layer * .18f), f.x() + Math.cos(a) * radius, f.y() + 48 + Math.sin(a) * radius * .45,
                        f.x() + Math.cos(b) * radius, f.y() + 48 + Math.sin(b) * radius * .45, layer == 0 ? 4 : 2);
            }
        }
        for (int i = 0; i < 20; i++) {
            double phase = (time * 1.7 + i * .137) % 1, angle = i * 2.399 + time;
            double radius = 130 * (1 - phase), x = f.x() + Math.cos(angle) * radius, y = f.y() + 50 + Math.sin(angle) * radius;
            worldLine(batch, color(i % 3 == 0 ? WHITE : element, (float) phase), x, y, x + Math.cos(angle) * 13, y + 18, 3);
        }
    }
    private void bolt(Batch batch, double x, double targetY, double top, long seed) {
        double px = x, py = top;
        for (int i = 1; i <= 9; i++) { double nx = x + Math.sin(seed + i * 3.7) * 40, ny = top + (targetY - top) * i / 9;
            worldLine(batch, color(CYAN, .3f), px, py, nx, ny, 16); worldLine(batch, WHITE, px, py, nx, ny, 3); px = nx; py = ny; }
    }
    private Color color(Color c, float alpha) { return tint.set(c.r, c.g, c.b, Math.max(0, Math.min(1, alpha))); }
    private void fill(Batch b, Color c, float x, float y, float w, float h, float alpha) { b.setColor(c.r, c.g, c.b, alpha); b.draw(theme.assets.pixel, getX() + x, getY() + y, w, h); }
    private void worldRect(Batch b, Color c, double x, double y, double w, double h, float alpha) { b.setColor(c.r, c.g, c.b, alpha); b.draw(theme.assets.pixel, camera.x(x), camera.y(y), (float) w * camera.unit(), (float) h * camera.unit()); }
    private void worldLine(Batch b, Color c, double x, double y, double xx, double yy, float width) { art.line(b, c, camera.x(x), camera.y(y), camera.x(xx), camera.y(yy), width * camera.unit()); }
    private void ring(Batch b, Color c, double x, double y, double r, float width, float alpha) { for (int n = 0; n < 32; n++) { double a = n * Math.PI / 16, next = (n + 1) * Math.PI / 16; worldLine(b, color(c, alpha), x + Math.cos(a) * r, y + Math.sin(a) * r, x + Math.cos(next) * r, y + Math.sin(next) * r, width); } }
    private void box(Batch b, Color c, double x, double y, double w, double h) { worldLine(b,c,x,y,x+w,y,1); worldLine(b,c,x,y+h,x+w,y+h,1); worldLine(b,c,x,y,x,y+h,1); worldLine(b,c,x+w,y,x+w,y+h,1); }
    private void text(Batch batch, String value, float x, float y, float scale, Color c) { typography.draw(batch, value, x + 30 * scale, y, 19 * scale, 600 * Math.max(.1f, scale), c, 1); }
}
