package dev.gamecore.client.fight;

/** 原创线稿图标。坐标归一化，断开的笔画单独保存；不依赖字体符号、外部图标包或额外纹理。 */
public enum FightIcon {
    FIST(new float[]{.12f,.18f,.12f,.55f,.27f,.55f,.27f,.82f,.42f,.82f,.42f,.61f,.45f,.88f,.60f,.88f,.60f,.62f,.63f,.82f,.77f,.82f,.77f,.57f,.80f,.71f,.92f,.67f,.92f,.37f,.69f,.13f,.30f,.13f,.12f,.18f}, new float[]{.15f,.50f,.45f,.43f,.55f,.29f}),
    BOLT(new float[]{.57f,.94f,.16f,.48f,.45f,.48f,.34f,.07f,.87f,.60f,.58f,.60f,.57f,.94f}),
    WEB(new float[]{.50f,.08f,.50f,.92f},new float[]{.08f,.50f,.92f,.50f},new float[]{.18f,.18f,.82f,.82f},new float[]{.18f,.82f,.82f,.18f},new float[]{.50f,.80f,.72f,.72f,.80f,.50f,.72f,.28f,.50f,.20f,.28f,.28f,.20f,.50f,.28f,.72f,.50f,.80f},new float[]{.50f,.64f,.64f,.50f,.50f,.36f,.36f,.50f,.50f,.64f}),
    ULTIMATE(new float[]{.50f,.98f,.61f,.65f,.86f,.82f,.72f,.56f,.98f,.50f,.70f,.41f,.84f,.15f,.60f,.29f,.50f,.02f,.40f,.29f,.14f,.15f,.29f,.42f,.02f,.50f,.30f,.59f,.16f,.84f,.40f,.68f,.50f,.98f},new float[]{.43f,.42f,.57f,.58f}),
    SHIELD(new float[]{.50f,.94f,.88f,.78f,.82f,.36f,.65f,.16f,.50f,.07f,.35f,.16f,.18f,.36f,.12f,.78f,.50f,.94f},new float[]{.33f,.52f,.46f,.37f,.70f,.65f}),
    PLAY(new float[]{.25f,.13f,.87f,.50f,.25f,.87f,.25f,.13f}),
    PAUSE(new float[]{.30f,.16f,.30f,.84f},new float[]{.70f,.16f,.70f,.84f}),
    RESTART(new float[]{.24f,.67f,.39f,.85f,.66f,.85f,.84f,.64f,.84f,.35f,.64f,.15f,.37f,.15f,.18f,.35f},new float[]{.20f,.93f,.20f,.62f,.50f,.62f}),
    MAP(new float[]{.10f,.15f,.35f,.26f,.65f,.12f,.90f,.26f,.90f,.86f,.65f,.74f,.35f,.88f,.10f,.75f,.10f,.15f},new float[]{.35f,.26f,.35f,.88f},new float[]{.65f,.12f,.65f,.74f}),
    HOME(new float[]{.08f,.48f,.50f,.89f,.92f,.48f},new float[]{.22f,.52f,.22f,.12f,.78f,.12f,.78f,.52f},new float[]{.42f,.12f,.42f,.43f,.59f,.43f,.59f,.12f}),
    CLOCK(new float[]{.50f,.92f,.79f,.80f,.92f,.50f,.79f,.20f,.50f,.08f,.21f,.20f,.08f,.50f,.21f,.80f,.50f,.92f},new float[]{.50f,.78f,.50f,.50f,.70f,.39f}),
    HEART(new float[]{.50f,.12f,.14f,.47f,.10f,.67f,.23f,.84f,.40f,.84f,.50f,.71f,.60f,.84f,.77f,.84f,.90f,.67f,.86f,.47f,.50f,.12f}),
    TROPHY(new float[]{.27f,.87f,.73f,.87f,.68f,.52f,.50f,.37f,.32f,.52f,.27f,.87f},new float[]{.27f,.78f,.10f,.78f,.13f,.51f,.32f,.44f},new float[]{.73f,.78f,.90f,.78f,.87f,.51f,.68f,.44f},new float[]{.50f,.37f,.50f,.15f,.27f,.15f,.73f,.15f});

    private final float[][] paths;
    FightIcon(float[]... paths) { this.paths=paths; }
    public int paths() { return paths.length; }
    public int points(int path) { return paths[path].length/2; }
    public float x(int path,int point) { return paths[path][point*2]; }
    public float y(int path,int point) { return paths[path][point*2+1]; }
}
