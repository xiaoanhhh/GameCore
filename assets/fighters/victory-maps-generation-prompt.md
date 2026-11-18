# 地图与功夫庆祝素材生成记录（2026-10-21）

使用内置 image_gen；角色沿用本项目已生成的参考图集作为身份/风格参考，保留用户上传图片中奶蛙的绿色蛙眼、肥嘟嘟的长耳与深色鼻子。没有使用其他游戏的地图或角色素材。生成结果直接复制到项目，未修改像素；JSON 是读取 alpha 实测边界和锚点，没有重新裁图。

| 项目文件 | 实际规格 | SHA-256 |
| --- | --- | --- |
| fighters-victory-v1.png | 1254×1254 RGBA，16 姿态 | 7fffa98a75933c457febf80b2656609b721d3786e9db7c5e719b3a2ea3e569b0 |
| ../backgrounds/fight-stage-temple-v1.png | 1672×941 RGB | f1af9dd318be48dc1280430ee01a35967d7742f4810b459a6ff887fc864e7e03 |
| ../backgrounds/fight-stage-forge-v1.png | 1672×941 RGB | 9c727cb6c1a82370a472f69b9bf3592fddb0f0a8e13aa87628975a930c061ba0 |

模型未按请求输出 2048 或 1920 尺寸，以上记录实物。每角色八个庆祝姿态：抱拳、马步、左右踏拳、鹤立、侧踢、转身、举拳；左右踏拳的生成朝向相近，运行时对其中一式镜像。姿态图是二维精灵，非三维骨骼/连续逐帧动画。原参考角色作者与分发权仍未提供，生成素材不替代原角色授权；本轮未发布。

原始生成文件名依次为 exec-07bc97fb-0b06-4aca-aa0f-c5f70f6e3afb.png、exec-d615cdb4-3063-4110-b128-5b26915b51a3.png、exec-86f281c7-d6ba-4045-8338-9d95ac807561.png。运行时只读取项目 assets 中的文件。

## 最终完整提示词：功夫庆祝

```text
Use case: stylized-concept. Asset: transparent KUNG FU VICTORY DANCE sprite atlas for an existing fighting game. Input is an identity/style reference, not an edit target. Preserve exact characters: 奶蛙 round smooth head, prominent GREEN frog eyes, massive cream pear belly, broad brown toes; 肥嘟嘟 LONG rounded ears, large dark red-brown nose, white eyes, massive cream belly. Both very plump yellow, bare hands, no clothing or accessories, soft volumetric 3D render matching reference. Exactly SIXTEEN full-body figures in FOUR columns FOUR rows with large transparent gaps. Rows1-2 奶蛙, rows3-4 肥嘟嘟. Eight different poses for EACH character in reading order: 0 kung fu respect salute, fist against open palm at chest; 1 deep wide HORSE STANCE knees bent fists pulled to waist; 2 celebratory dance step leaning LEFT with one fist punching left and opposite foot crossing inward; 3 mirrored dance step leaning RIGHT with opposite fist punching right; 4 CRANE pose balanced on one leg, raised knee high, arms curved outward; 5 powerful high SIDE KICK leg extended to right at waist height, other leg supporting ground; 6 joyful TWIRLING pose three-quarter BACK view with torso rotating and arms out, feet crossed; 7 grand VICTORY pose fist raised overhead and other hand on hip with a confident smile. These must be visibly different kung fu dance moves, not eight repeated standing figures. Body size consistent, dynamic poses but keep enormous belly and character identity. Most face generally RIGHT; pose6 intentionally turning back. Every full character entirely inside its own cell, no chopped toes/ears/hands, no overlap. Actual transparent alpha background. Square ideally 2048x2048. NO floor, shadows on floor, text, labels, grid, effects or extra figures.
```

## 最终完整提示词：云巅武馆

```text
Use case: stylized-concept. Asset: panoramic background for a side-view arcade fighting stage, no fighters or UI. A magnificent traditional Chinese kung fu temple courtyard suspended above a sea of clouds at golden sunset. Deep vermilion wooden columns and sweeping dark tiled roofs frame the sides, distant layered blue mountains, giant ancient pine and cherry trees, floating pink petals, hanging red lanterns and warm golden sunbeams. Atmospheric painterly 3D game concept art, rich amber/cream/teal/coral palette, serene martial arts fantasy. Open spacious central lower combat area, dark weathered stone floor occupies bottom15 percent, floor horizon near bottom. Darker lower-middle so small yellow fighters and bright cyan/purple skills remain readable. No foreground floating platforms (these are drawn by code), people, characters, lettering, signage, numbers or UI. Wide landscape ideally 1920x1080; cinematic depth, polished premium game art. Do not copy an existing videogame scene.
```

## 最终完整提示词：熔火铸场

```text
Use case: stylized-concept. Asset: panoramic background for a side-view arcade fighting stage, no fighters or UI. A titanic volcanic foundry inside an obsidian caldera at night, enormous iron arches and industrial furnaces on both sides, flowing lava rivers far behind the playable floor, molten orange light, distant eruptions and smoky red sky, glowing forge machinery with chains and amber vents. Dramatic painterly 3D game concept art, charcoal black/red/copper/molten gold palette, powerful atmosphere and depth. Open spacious central lower combat area, safe dark black-steel floor occupies only bottom15 percent, floor horizon near bottom; lava stays in distant BACKGROUND and does not cover arena floor. Keep central foreground dark enough for small yellow fighters and cyan/purple attacks to read. No foreground floating platforms, people, fighters, text, logos, signs, numbers or UI. Wide landscape ideally1920x1080. Original scene, do not copy an existing game.
```
