# 冰火竞技背景

当前默认平台背景为 `lobby-moon-garden-v1.png`，1672×941 RGB，2,483,086字节，内置 image_gen 生成后逐字节复制；[完整提示词、SHA-256和使用边界](lobby-generation-prompt.md)。ClientAssetManager 每窗口一次加载与释放，GardenHero 借用同图裁切，不重复创建纹理；原冰火图与五张格斗背景保留。下列“全窗默认冰火”描述为历史行为。

最新格斗有五背景：原三图另加 `fight-stage-bamboo-v1.png`（竹影溪谷）与 `fight-stage-frost-v1.png`（霜月遗迹），新图各 1672×941 RGB。[来源/实际规格/完整提示词/哈希](scenery-generation-prompt.md)。屋顶/廊桥/柱子等前景由 FightSceneryRenderer 绘制，使用[六格建筑材质](../scenery/README.md)，预览与正式地图共用。地图页会缓存五背景，约 30 MiB RGBA 尺寸上界，材质另约 6 MiB，退出按窗口统一释放。以下三图说明为上一轮历史。

最新两页出战选择复用以下三图，不新增背景。地图页同时显示三缩略图与大预览，会把三张背景加载到同一窗口缓存；返回战斗重复使用，退出统一释放。选角页和预览按比例显示/裁切，图像像素不修改。

最新格斗地图包括原 `fight-stage-v1.png`（霓虹天城）及新增 `fight-stage-temple-v1.png`（云巅武馆）、`fight-stage-forge-v1.png`（熔火铸场）；新增两图均为内置 image_gen 原创场景，实际 1672×941 RGB。[完整提示词、SHA-256 与来源](../fighters/victory-maps-generation-prompt.md)。FightMapStyle 选择背景和色调，FightBoard 绘制飘花/余烬等装饰；纯 Java FightMap 决定平台几何。窗口按需缓存至多三张，释放时全部销毁，每图 RGBA 尺寸上界约 6 MiB。

格斗棋盘另有新生成的 `fight-stage-v1.png`，蓝紫悬空都市，实际 1672×941 RGB；由内置 image_gen 生成，SHA-256 `751a8890d48cd6e4a411c2975ad4f53835f76c4f0b79ca2547e87b51fbf0093d`。[完整提示词与来源记录](../fighters/presentation-generation-prompt.md)。仅作为棋盘内装饰，缺失回退程序场景；原大厅背景保持下述用户来源及许可声明，不互相推定授权。

- 文件：`arena-background.png`，1672×941，3,239,581 字节。
- SHA-256：`714c1a69a7152ec5426fdc0dce4882964a05ffb75f924a43e5095deda682817d`。
- 来源：用户在 2026-10-20 本轮对话中上传的最新附件；并明确选择“自有或已获分发授权”。依据该声明随本项目打包分发，不自行赋予 CC、MIT 等开源许可证。作者及原始制作工具未提供，不作推定。
- 处理：原文件逐字节复制，未重新生成、修图或提高分辨率。运行时保持比例居中裁切，叠加程序生成的深色遮罩与渐变；原图不变。
- 入口：ClientAssetManager 负责每窗口一次加载和释放；BackdropRenderer 在 UI 之前绘制整个客户区。图片失败回退为主题底色，游戏和设置继续使用。
- 成本：原分辨率 RGBA 纹理每窗口约 6.0 MiB，不含驱动及其余字体/资源开销；未生成 mipmap。高分辨率窗口允许背景柔化，字体按独立密度机制保持清晰。

仓库根目录 `background.png` 为此前上传版本，本轮加载与打包使用此目录文件，不读取本机绝对路径或临时附件路径。
