# 格斗开场与舞台素材（2026-10-21）

通过内置 image_gen 生成，未调用 CLI/API。问候图以已有 `fighters-boxing-v1.png` 为身份/风格参考，保留奶蛙绿眼、肥嘟嘟长耳/深红鼻和黄胖体型。舞台为新生成的无角色背景。原角色作者与分发许可仍未核实，生成工具不能替代权属证明。

| 文件 | 实际规格 | SHA-256 |
| --- | --- | --- |
| [fighters/fighters-greetings-v1.png](fighters-greetings-v1.png) | 1254×1254 RGBA，两角色各八姿态，透明 | ce156785757470c8c8d07b6fd80a9d9eaf06d991fea2fe8df3fad19481358cdf |
| [backgrounds/fight-stage-v1.png](../backgrounds/fight-stage-v1.png) | 1672×941 RGB，不透明 | 751a8890d48cd6e4a411c2975ad4f53835f76c4f0b79ca2547e87b51fbf0093d |

尺寸与提示期望有偏差，以上为实际文件，未称为 2048² 或 1920×1080。问候八姿态是挥手两帧、鞠躬两帧、点赞两帧和敬礼/举拳两帧，通过时间轴选姿态；不是骨骼或完整逐帧动画。`fighters-greetings-v1.json` 记录 alpha 连通区域实测矩形和身体/脚底锚点，不平均切格，也没有用脚本修改图片像素。文件已复制到项目 assets，从工作区打包，不依赖生成目录。

## 问候完整提示词

Use case: stylized-concept. Asset: production transparent greeting sprite sheet for an existing fighting game. Input image is ONLY a character identity and soft 3D style reference. Preserve these exact two very plump yellow characters: 奶蛙 with rounded head and prominent GREEN frog eyes, enormous cream pear belly and brown broad toes; 肥嘟嘟 with two LONG rounded ears, enormous cream belly and large dark red-brown nose, white eyes. Bare hands, no clothing, no accessories. Exactly SIXTEEN separate FULL BODY figures in FOUR COLUMNS and FOUR ROWS, big transparent gutters, every ear/hand/toe contained. Rows 1-2 奶蛙, rows 3-4 肥嘟嘟. Eight poses per character in reading order: 0 friendly greeting with open hand raised high, palm toward viewer; 1 opposite waving hand tilt with cheerful face; 2 upright respectful palms together bow preparation; 3 distinctly BOWED torso forward about 30 degrees hands together; 4 playful THUMBS UP with confident grin and other hand on hip; 5 energetic double thumbs-up leaning forward; 6 cool SALUTE fingers near forehead; 7 finish salute with fist raised proudly overhead. All front three-quarter view looking generally RIGHT, facing the opponent, keep the round plump identity. Same body size and feet baseline within each cell; no perspective size differences. Large empty gaps; no overlap. Soft volumetric studio render as reference. Square canvas ideally 2048x2048. Actual transparent alpha, NO background, floor, shadows on floor, text, grid, labels, effects or extra characters.

## 舞台完整提示词

Use case: stylized-concept. Asset: seamless-looking wide background art for a cinematic arcade fighting game. Create a breathtaking dark futuristic floating arena at night above a vast cyberpunk city, dramatic painterly 3D concept art, deep midnight navy with cyan electric light on the LEFT and violet-magenta energy on the RIGHT. Huge circular reactor / distant eclipse at the center of the skyline, vast layered towers, distant spotlights, angular suspended architecture and clouds, subtle luminous particles, strong atmospheric depth. A dark metallic arena floor occupies only the bottom 15 percent, reflective subtle cyan/violet rails, floor horizon near bottom. Leave the entire central lower middle combat area spacious and dark so small yellow fighters and cyan/purple projectiles read clearly. Cinematic anamorphic composition, 16:9 landscape ideally 1920x1080. No fighters, no people, no foreground platforms, no text, logos, numbers or UI. Polished rich dramatic game artwork, not a flat vector illustration or wireframe. Avoid overly bright white center and noisy foreground details.
