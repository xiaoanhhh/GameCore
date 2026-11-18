# 客户端资源目录

2026-10-25阶段03在线Tank新增中文连接/协议/异常提示，复用现有地图、图块、背景与几何渲染。Noto/OFL同源字体通过原脚本扩至1110字符，正文清单为`GameCoreUISans-e86af88c1af7a3a5.ttf`，展示为`GameCoreDisplay-e27c83522550e937.ttf`；完整来源/哈希见[字体登记](../opensource_application/NotoSansSC/使用说明.md)。旧字体与素材保留，地图/图块及试听WAV内容未变，没有新图片或音轨。以下为此前视觉交付。

当前默认大厅使用内置 image_gen 新生成的月色花园插画，来源、完整提示词、哈希及分发边界见 [记录](backgrounds/lobby-generation-prompt.md)。HUD 五种地图边饰、月牙/书架/设置图标、光轨与面板均为项目代码原创，复用白像素/圆角，不引入 icon 包或运行/传递依赖。原冰火图保留为历史素材。2026-10-22 地图 HUD / 月色花园 UI：同源 Noto Sans SC、OFL，1096 字符，正文450 `GameCoreUISans-e9d0367cdeeae89c.ttf`，SHA-256 `e9d0367cdeeae89c307c20e982c709ce08fa557a9ccc0ec0aad7df4dc6658de9`；展示900 `GameCoreDisplay-f4dd328d82fb400d.ttf`，SHA-256 `f4dd328d82fb400dc0aa697a732403c0b7ca863ef0bdeff6926c651a264935fd`。脚本、已校验源与 FontTools4.63.0不变，未覆盖旧字体。 以下为历史记录。

大招/闪袭增量复用原角色动作与窗口 pixel/circle，暗云/斜雷/丝束/茧/爆裂/传送门均为项目代码原创，无新增图片、音轨或外部素材。[代码入口](../02-可扩展游戏平台架构/README.md)。同源 Noto/OFL 字体为1077字符，正文450清单 `GameCoreUISans-ed8c85b1de75e908.ttf`，展示900为 `GameCoreDisplay-afba5e6b158d06c3.ttf`；以下为历史。
打击/UI 增量仅有项目代码原创的 13 枚线稿图标、裁角面板和局部命中几何，复用白像素/circle，无新位图、音轨或第三方 icon 包。[入口与来源](../02-可扩展游戏平台架构/README.md)。同源 Noto/OFL 为 1049 字符，正文 450 清单 `GameCoreUISans-84321bb01f1ba6d1.ttf`，展示 900 为 `GameCoreDisplay-13febf0bb2f9a5c3.ttf`；以下为历史。
五图场景增量：新增 `backgrounds/fight-stage-bamboo-v1.png`、`fight-stage-frost-v1.png`（各 1672×941 RGB）和 `scenery/fight-materials-v1.png`（1536×1024 RGB、六格材质），均由内置 image_gen 生成后直接保存，未改像素。[背景完整提示词/哈希](backgrounds/scenery-generation-prompt.md)、[材质来源与提示词](scenery/README.md)。前景建筑由代码按真实顶沿/边界绘制，五背景及一材质纹理共享缓存。当前同源 Noto/OFL 为 1034 字符，正文 450 清单 `GameCoreUISans-03b586b3b787e9ac.ttf`，展示 900 为 `GameCoreDisplay-2c5306731aa40456.ttf`；以下均为历史。

奶蛙大笑新增内置 image_gen 的 `fighters/fighters-laugh-v1.png`/JSON，1774×887 RGBA、八姿态，[完整提示词/来源/哈希](fighters/laugh-generation-prompt.md)。视频、参考帧及音轨不打包。纹理仍按窗口缓存释放，约 6 MiB 尺寸上界。当前同源 Noto/OFL 为 1010 字符，正文 450 清单 `GameCoreUISans-dad11a39816acc73.ttf`，展示 900 为 `GameCoreDisplay-338f98450543f040.ttf`；以下数量为历史。

出战选择界面复用现有原角色图集和三张地图，没有新增位图、音效或第三方素材。新增边框/元素预演由程序绘制；地图页三张缩略图会加载三背景到窗口缓存（约 18 MiB RGBA）。同源 Noto/OFL 正文/展示子集扩至 1003 字符，当前清单选择 `GameCoreUISans-398d0f01e53280a9.ttf`（450）、`GameCoreDisplay-777aa5324e7dd720.ttf`（900）。[界面模块](../02-可扩展游戏平台架构/README.md)、[字体来源及完整哈希](../opensource_application/NotoSansSC/使用说明.md)。以下数量均为历史。

地图/庆祝新增：`fighters/fighters-victory-v1.png`/JSON 及 `backgrounds/fight-stage-temple-v1.png`、`fight-stage-forge-v1.png`，均由内置 image_gen 生成并复制到项目。[完整提示词/来源/哈希](fighters/victory-maps-generation-prompt.md)。当前正文/展示均 989 字符，清单分别选择 `GameCoreUISans-f150b9c35ac29e17.ttf`（450）、`GameCoreDisplay-51733d69e510b6b5.ttf`（900），来源、版权和 OFL 不变。以下较早子集和区域数为历史。

街机开场增量：`fighters/fighters-greetings-v1.png`/JSON 与 `backgrounds/fight-stage-v1.png` 为内置 image_gen 生成资源，[完整提示词/来源/哈希](fighters/presentation-generation-prompt.md)。字体正文清单现为同源 450 字重 `GameCoreUISans-158739b079b70c7b.ttf`（959 字符）；新增 `fonts/display-font-file.txt` 选择同源静态 900 字重 `GameCoreDisplay-789d3c14301de756.ttf`，格斗标题按需栅格化，正文不加粗。OFL 及原版权保留，旧文件不覆盖。

拳击补充：新增 `fighters/fighters-boxing-v1.png` 及 JSON，16 个实测区域，未覆盖 V3；[来源与完整提示词](fighters/boxing-generation-prompt.md)。当前字体已扩到 926 字符，由清单选择 `GameCoreUISans-4d5a5abe5c44e51d.ttf`，仍沿用同源 Noto/OFL。下文为 V2 及以前的资源记录。

格斗 V2 当前加载 `fighters/fighters-v3.json` 与三张 V3 动作图集，每角色 24 姿态，旧图集保留为封面/头像及异常回退。雷电、蛛网、尘土、冲击环和霓虹舞台由代码绘制。[V3 生成提示词、尺寸/哈希与来源](fighters/v3-generation-prompt.md)。字体清单现选择 903 字符的同源 Noto/OFL 子集 `GameCoreUISans-c1673faf54013e53.ttf`。以下为 V1 及更早资源记录。

终极格斗角色位于 `fighters/fighters-reference-v2.png`，用内置 image_gen 依据用户原图制作透明 3D 质感动作精灵；[完整生成提示词与来源记录](fighters/generation-prompt.md)。舞台、闪电/蛛网继续由 Java 代码绘制并复用 circle/pixel。原 JPG 未直接打包，角色分发权仍未核实，详见 [角色与素材说明](../02-可扩展游戏平台架构/README.md)。字体同源子集扩至 870 字符，当前清单选择 `GameCoreUISans-6d5c8d8c80ee8bcb.ttf`，仍保留 OFL。新加入格斗模块的源码由素材脚本一并扫描。

第 3 步新增 `maps/tank/crossfire.tmx`、`ruins.tmx` 和 `arena-tiles.png`，均为项目原创，由根目录 `scripts/prepare-tank-maps.py` 可复现生成。TMX 为标准 1.10 子集，PNG 为原创几何色块，不来自 Tiled 示例或外部游戏。编辑地图后不要直接重跑生成脚本覆盖修改；运行时不需要 Python 或 Tiled。来源与格式约束见 [地图模块](../02-可扩展游戏平台架构/README.md)和[独立登记](../opensource_application/Tiled/使用说明.md)。

由 Gradle run 作为工作目录，并作为 lwjgl3 的 main resources 打包输入。

当前由 `fonts/font-file.txt` 选择按内容哈希命名的 `GameCoreUISans-*.ttf`（Noto Sans SC 的改名字体子集，许可随 OFL.txt 保留），另有字符表和 `audio/preview.wav`（项目脚本合成试听/吃食物音）。旧字体及历史内容哈希子集保留以避免替换 Windows 旧进程映射的文件；当前加载器只使用清单指定文件。Snake 棋盘、食物、双蛇和封面均由代码原创绘制，没有外部 skin。

全窗背景为用户上传并确认分发授权的 `backgrounds/arena-background.png`，原图 1672×941；运行时等比裁切并加深色遮罩。来源、原文件哈希和使用边界见 [背景声明](backgrounds/README.md)。程序渐变、圆角和细边框均为运行时原创绘图，不新增依赖。
素材重建脚本为仓库根目录 `scripts/prepare-lobby-assets.py`；日常构建直接使用已提交资源。
新增资源时记录来源、许可、作者、修改与分发要求，再更新开源/素材使用说明。
路径使用相对 assets 的名称，避免写死本机绝对路径。
