package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import dev.gamecore.fight.FightMap;

/** 地图 HUD 视觉契约：装饰材料、两侧识别色与数字可读底色。只属于 client，不改变规则或碰撞。 */
public enum FightUiSkin {
    NEON("CIRCUIT", "NEON SKY CITY", "4edbff", "eb70ff", "081529", "45637f", "67ffd0"),
    TEMPLE("CLOUD", "CLOUDTOP DOJO", "ffd28b", "ffa8bb", "29202e", "927762", "c5eea1"),
    FORGE("STEEL", "EMBER FOUNDRY", "ff9a50", "ffd96c", "24191e", "977b67", "ffd370"),
    BAMBOO("BAMBOO", "BAMBOO CREEK", "a9e6ad", "e9d79a", "122a29", "648d78", "b5efaa"),
    FROST("CRYSTAL", "FROSTMOON RUINS", "a3edff", "c1acff", "151d36", "7188ac", "b8eeef");

    public final String material, subtitle;
    public final Color primary, secondary, panel, metal, health;
    FightUiSkin(String material,String subtitle,String primary,String secondary,String panel,String metal,String health) {
        this.material=material;this.subtitle=subtitle;this.primary=Color.valueOf(primary);this.secondary=Color.valueOf(secondary);
        this.panel=Color.valueOf(panel);this.metal=Color.valueOf(metal);this.health=Color.valueOf(health);
    }
    public Color side(int player){return player==0?primary:secondary;}
    public static FightUiSkin of(FightMap map){return valueOf(map.name());}
}
