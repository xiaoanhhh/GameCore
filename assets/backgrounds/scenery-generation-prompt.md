# 场景扩充背景来源（2026-10-21）

使用 imagegen 技能的内置 image_gen 工具分别生成两张原创场景。没有复制其他游戏地图。生成文件逐字节复制到项目；前景屋顶、柱子、栈道与断桥由 FightSceneryRenderer 按真实规则顶沿绘制，不修改背景像素。

| 文件 | 实际规格 | SHA-256 |
| --- | --- | --- |
| fight-stage-bamboo-v1.png | 1672×941 RGB | 62c9df4e66fb9c7458729b0dc5f1b83bccabca7a396cf09f82296d3be5acfecb |
| fight-stage-frost-v1.png | 1672×941 RGB | fa3275a020e1fc5c67913a9b9a491c79347e9514ddf124c28a0692d6e6f8ab01 |

模型未按请求输出 1920×1080，以上按实物记录。原始生成文件名依次为 exec-847ffeaa-6fb7-47c7-9ebd-33d82b46f27f.png、exec-665ba567-e23f-4186-8d1b-d61a77189b8b.png；运行时只读项目 assets，不依赖本机生成目录。原有三张背景及角色来源限制保留，各自授权不互相推定。

## 竹影溪谷完整最终提示词

```text
Use case: stylized-concept. Asset: original panoramic background for a side-view arcade fighting stage. A lush emerald bamboo valley in a Chinese martial arts fantasy, turquoise mountain stream winding through mossy rocks, enormous bamboo stalks and ancient trees framing the sides, a distant wooden waterwheel and rope bridges above mist, warm shafts of sunlight through the green canopy, floating fireflies and fallen leaves. Painterly polished 3D game concept art matching a premium cinematic fighting-game stage, deep teal/emerald/jade with warm amber highlights. Open spacious central lower combat area, safe dark weathered wooden-and-stone terrace floor occupies bottom 12 percent, floor horizon near bottom. Keep lower-middle relatively dark for small yellow fighters and bright elemental skills. All climbable foreground buildings, bamboo walkways and pillars will be rendered by code, so no foreground floating platforms or close-up obstacles. No people, fighters, text, logos, signage, symbols, numbers or UI. Wide landscape ideally 1920x1080; original composition, do not copy an existing video game.
```

## 霜月遗迹完整最终提示词

```text
Use case: stylized-concept. Asset: original panoramic background for a side-view arcade fighting stage. Moonlit frozen ruins of an ancient mountain citadel, massive fractured pale stone arches and frost-covered columns framing both sides, distant ruined palace towers, a luminous blue full moon and violet aurora above snowy mountains, fine drifting snow, ice-blue light glowing between worn stone slabs. Painterly polished 3D game concept art matching a premium cinematic fighting-game stage, midnight indigo/ice cyan/silver/violet palette, grand quiet magical atmosphere. Open spacious central lower combat area, safe dark frost-dusted stone courtyard floor occupies bottom 12 percent, floor horizon near bottom. Keep lower-middle dark enough for small yellow fighters. All climbable foreground broken bridges, pillar caps and altars will be drawn by code, so no close-up foreground platforms or obstacles. No people, fighters, text, logos, signage, numbers, symbols or UI. Wide landscape ideally 1920x1080; original scene, do not copy an existing videogame.
```
