package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import dev.gamecore.fight.FightMap;

/** 地图身份到美术资源/主题色的客户端映射；图片与天气只影响渲染，不向纯 Java 规则泄露。 */
public enum FightMapStyle {
    NEON("backgrounds/fight-stage-v1.png","59e8ff","b77bff","101d35"),
    TEMPLE("backgrounds/fight-stage-temple-v1.png","ffcf81","f28caa","392c35"),
    FORGE("backgrounds/fight-stage-forge-v1.png","ff784f","ffce67","28191e"),
    BAMBOO("backgrounds/fight-stage-bamboo-v1.png","a5e284","e4c783","102f2a"),
    FROST("backgrounds/fight-stage-frost-v1.png","a2dfff","c0a7ff","16243b");
    public final String background;
    public final Color primary,secondary,ground;
    FightMapStyle(String background,String primary,String secondary,String ground){this.background=background;this.primary=Color.valueOf(primary);this.secondary=Color.valueOf(secondary);this.ground=Color.valueOf(ground);}
    public static FightMapStyle of(FightMap map){return switch(map){case NEON->NEON;case TEMPLE->TEMPLE;case FORGE->FORGE;case BAMBOO->BAMBOO;case FROST->FROST;};}
}
