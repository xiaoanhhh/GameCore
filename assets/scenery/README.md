# 建筑材质图集（2026-10-21）

`fight-materials-v1.png` 由 imagegen 技能的内置 image_gen 生成，实际 1536×1024 RGB，六格各 512×512。SHA-256 `71edfafc9e9897d19827f5db8a35a93fae16a27fe93c3d3687c8932e0c96ab98`。请求 2048×1366 未按该尺寸输出，按实物记录；未修改像素。原始生成文件名 exec-7730a449-f2e2-44f5-95d8-ed4e9c0a52a9.png，已复制到本目录。

从左到右、从上到下为都市玻璃窗、青绿瓦片、铆钉钢板、竹木、苔藓溪石、冰霜石砖。FightSceneryMaterial 每格内缩 3 像素避免线性采样串色。FightSceneryRenderer 按世界单位平铺 UV、叠加程序体积/阴影/刻线；实际落脚面来自规则，素材不决定碰撞，也没有新的空中图片平台。

一张纹理供五图与预览共享，按 RGBA 尺寸上界约 6 MiB/格斗窗口，惰性加载并由 ClientAssetManager 统一释放；缺失回退程序材质。未借用外部游戏美术，未新增运行/传递库。原角色/旧背景来源与授权限制各自保持。

## 最终完整提示词

```text
Use case: stylized-concept. Asset: a six-material texture atlas for architectural foreground structures in a premium side-view fighting game. Exactly SIX equal rectangular panels arranged THREE columns by TWO rows, edge-to-edge, no gaps, no borders, no labels. Each panel is a flat front-facing evenly lit material sample filling its full panel, no perspective scene, no isolated objects, no text. Painterly realistic 3D game material detail, subtle directional light and surface roughness, readable at small scale. Reading order: 0 midnight navy futuristic building facade surface with small cyan glass windows, thin brushed blue metal frames, subtle weathering; 1 rows of traditional Chinese jade-green glazed curved ceramic ROOF TILES, aged edges and restrained moss, warm patina, dense overlapping curved tile scales; 2 blackened industrial steel plates with copper rivets, scratches, soot and narrow amber-orange heated seams, dark iron; 3 vertical bundles of aged green bamboo poles and weathered brown timber boards, natural knots, fibers and rope lashings, warm jade and ochre; 4 rough mossy gray-green river stone with faceted cracks, lichen and a few soft emerald moss patches, no plants outside the surface; 5 pale blue-gray ancient carved granite blocks coated in frost, fine icy cracks and silver ice crystals, stone seams. The panels must be clearly different materials in these exact positions, individually tile-like and usable as texture regions. Avoid pictorial scenes, people, icons, signage, writing or logos. Landscape aspect ratio 3:2 ideally 2048x1366, all six panels same size.
```
