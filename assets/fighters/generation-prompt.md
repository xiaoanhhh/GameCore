# 角色图集生成记录

后续开场问候及新舞台记录见 [街机演出素材](presentation-generation-prompt.md)，保留完整提示词、实际输出和哈希。

- 模式：内置 image_gen 工具，透明背景，使用用户原图作为角色身份参考；未调用 CLI/API，也未使用本地脚本编辑图像。
- 日期：2026-10-20。
- 最终文件：fighters-reference-v2.png，1254×1254 RGBA。
- SHA-256：8d74a5d175671b2c900c1ec327f18b7f4cb9a23b6c35693ac117fc180b69a1a7。
- 参考原图 SHA-256：c9ffd82d3ddf1fbc858bc2fbbe48217643c8e7b9cd827ddd44c23b32ad31d92d。
- 第一版 6×2 图集的拳头跨越分格，故只保留第二版 3×4 布局的最终文件；实际输出尺寸/行距与提示要求有偏差，已按实测人物边界建立 TextureRegion。没有将期望尺寸当成真实结果。
- 奶蛙/肥嘟嘟分别有待机、出拳、格挡、远程、大招、受击六姿态。角色是透明 2D 动作精灵，保留参考图的 3D 质感；未生成可旋转的网格或骨骼模型。
- 原图未直接打包。参考角色的作者及分发权未提供；生成工具不构成对输入角色权利的核实。项目许可仍由所有者决定。

## 最终提示词

Use case: identity-preserve. Asset type: transparent 3D character animation sprite atlas for a fighting game. Image 1 is the ORIGINAL CHARACTER IDENTITY reference. Image 2 is an APPROVED IDENTITY/POSE reference but its layout is unsuitable: REARRANGE into a well-spaced atlas, preserving the soft 3D characters. Make a SQUARE 1536 x 1536 transparent PNG, a STRICT 3-column x 4-row grid of equal 512 x 384 cells. There must be HUGE clear transparent gutters between every sprite. Every character stays COMPLETELY WITHIN its own cell; maximum character width 330 px, max height 330 px, centered x in cell, feet at y=360 inside each cell. No limb or fist can cross a cell boundary, no clipping, no other sprite fragment in a cell. All sprites face RIGHT. Row 1: 奶蛙 ready stance / 奶蛙 straight extended punch / 奶蛙 guard with fists in front of face. Row 2: 奶蛙 ranged open-palm cast / 奶蛙 ultimate two-palm cast / 奶蛙 hurt recoil. Row 3: 肥嘟嘟 ready stance / 肥嘟嘟 straight extended punch / 肥嘟嘟 guard. Row 4: 肥嘟嘟 ranged open-palm cast / 肥嘟嘟 ultimate two-palm cast / 肥嘟嘟 hurt recoil. EXACTLY 12 separate complete full-body figures. Preserve original 奶蛙's enormous plump pear body, very rounded yellow head, protruding green eyes, tiny smile, huge cream belly, thick arms and short thick legs with broad brown toes. Preserve original 肥嘟嘟's enormous plump yellow pear body, two long rounded ears, HUGE dark reddish-brown oval NOSE as prominent as the original image, large white eyes with brown pupils, enormous cream belly, thick limbs and broad brown toes. Soft smooth volumetric 3D rendering faithfully matching image 1, not thin cartoons, no black drawn outlines. Neutral soft lighting. Consistent character size across poses, full body, all feet visible. TRANSPARENT ALPHA background; no ground, no floor shadows, no text/labels, no grid lines, no effects, no gradient background, no panels, no painted checkerboard.

## V3 疾跑与战斗动作扩展（2026-10-21）

三张图集、48 个实测区域及完整提示词见 [V3 生成记录](v3-generation-prompt.md)，实际边界见 fighters-v3.json。原图人物身份保持不变。

# 拳击动作补充

当前地面三拳增加独立透明图集，每角色八姿态，来源/最终完整提示词与实际哈希见 [拳击生成记录](boxing-generation-prompt.md)。已有图集保留作为其他动作及异常回退。

