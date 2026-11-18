# 奶蛙捧腹大笑素材记录（2026-10-21）

使用 imagegen 技能的内置 image_gen 工具生成。`fighters-greetings-v1.png` 是角色身份/风格参考，只取奶蛙；用户视频 `981fe4018b98685c00b244148bac83c0.mp4` 的关键帧仅作捧腹、仰头/前俯和肩膀抖动的动作参考，不复制视频界面、鼠标、背景、音轨或其他角色设计。原视频/关键帧未打包进游戏。

视频源 SHA-256：`2524afd4af1825365771b210832683b77faf3ebe9d60c5500f88349d9750d4b2`；OpenCV 读取为 262×388、242 帧、报告帧率约 28.924，帧数/帧率估计约 8.37 秒。提取六个分散关键帧观察，不据此声称逐帧复刻或音轨验证。视频及原角色作者/分发权未提供，引用动作不替代原角色授权。

生成图 `fighters-laugh-v1.png` 实际 **1774×887 RGBA**，SHA-256 `9fe0229adefe4be3bae08beefeb7a4d9301b68a16bbdd37d855cc7410a235306`。模型没有按请求输出 2048×1024，按实物记录。原始文件名 `exec-15e9364b-27a2-4321-bd50-12fc05b4a7a3.png`，已逐字节复制进本目录，不依赖本机生成目录。

八姿态：准备、捧腹笑、仰头、弯腰、左摇、右摇、擦笑泪、收尾。JSON 用 alpha>140 的八个主连通区域实测边界、脚底与躯干锚点；未修改图像像素。原图是二维多姿态精灵，不是三维网格、骨骼或完整连续逐帧动画。运行时镜像、轻微摇摆和挤压补充动作。

## 最终完整提示词

```text
Use case: stylized-concept. Asset: eight-pose transparent BELLY LAUGH sprite atlas for an existing arcade fighting game. Input image 1 is the character identity/style reference: use ONLY the round-headed yellow frog 奶蛙 from its first two rows, never the long-eared character. Input image 2 is MOTION reference only: borrow hands clutching the belly, big joyful open-mouth laughter and rocking forward/back, but do not copy its body design, UI, cursor or white background. Preserve 奶蛙 identity: smooth round head with NO ears, prominent GREEN frog eyes (green remains visible at eye edges when squinting), enormous cream pear-shaped belly, plump yellow limbs, broad brown toes. Bare hands, no clothing, no accessories, no tail. Soft volumetric polished 3D render matching input1. Exactly EIGHT full-body figures, FOUR columns TWO rows, large transparent gaps, consistent torso/body scale and foot baseline within each row. Reading-order poses: 0 small grin, both hands reaching to upper belly in anticipation; 1 laughing mouth open, both hands pressing belly, knees bent, shoulders bouncing; 2 leaning upper torso BACK with head thrown back and mouth wide open, both hands still clutching belly; 3 bent FORWARD laughing hard, shoulders rounded, belly compressed, eyes joyfully squeezed; 4 rocking LEFT with hands on belly and feet planted; 5 rocking RIGHT with hands on belly and feet planted; 6 one hand wiping a tiny joyful tear beside eye and the other supporting belly, mouth laughing; 7 settling into a warm chuckle, both hands relaxed on belly. These must be visibly distinct poses for a coherent short laugh animation, not eight identical idle figures. Generally three-quarter RIGHT-facing, entire character including toes/hands always inside each cell. Real transparent alpha background, NO ground or floor shadow, no text, letters, cursor, buttons, borders, effects or extra figures. Wide landscape ideally 2048x1024.
```
