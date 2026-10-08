## 简介

琉璃 (Glazuli) 是一个界面美化模组，重做了加载画面、主菜单和游戏内的各种界面。纯客户端，进服务器不受影响。

**注意：本模组为 AI 辅助开发。**

## 启动动画

- 红底 Mojang 画面加载完后，播放 Mojang Studios 标志动画和音效，镜头穿过标志进入主菜单场景，标题逐个字母落下，按任意键可跳过；
- 切换资源包、语言或按 F3+T 重载时同样使用红底画面，退出游戏时画面渐黑；
- 首次进入主菜单时会先播一段启动音效，再开始放菜单音乐。音效改编自 C418《Minecraft》开头的四个音，把自己的 `startup.ogg` 放进 `config/glazuli` 即可替换。

## 主菜单场景

原版全景图换成了 3D 场景。打开不同菜单时镜头会移到不同位置，进入世界时镜头直接冲进去。

- 自带场景：洞穴、繁茂洞穴、深暗之域、冰窟、下界、海底废墟、平原村庄·黄昏、雪原村庄·夜、沙漠村庄、远古城市、试炼密室、堡垒遗迹、末地城；安装机械动力后额外加入一个机器运转的工厂场景；
- 在世界中输入 `/menuscene save <名字>`，会把周围的方块保存到 `config/glazuli/scenes` 作为新场景，点主菜单的“场景”按钮切换；
- 室外场景的昼夜与电脑时间同步；
- 开启 Iris 光影时，场景也会用光影包渲染。

## 玻璃界面

- 面板、按钮、滑块、提示框、聊天栏、通知、Boss 血条和物品栏都换成磨砂玻璃效果；
- 按钮按下时会轻微压扁，切换界面时淡入淡出；
- 修改界面贴图的资源包照常显示原贴图，只保留动画效果；
- 主菜单右上角的按钮可把菜单换回原版，暂停菜单中另有开关单独控制游戏内界面；
- 字体换成鸿蒙黑体，系统装有苹方时使用苹方。把 `.ttf`、`.otf` 或 `.ttc` 文件放进 `config/glazuli/fonts` 可换成其他字体，也可以关闭自定义字体。

## 兼容性

- 钠：视频设置界面换成玻璃风格，基于 Reese的钠视频界面 修改，需要钠 0.8.13 及以上、低于 0.9；
- Iris Shaders：主菜单场景支持光影，Iris 自身界面的按钮换成玻璃风格；
- JEI：面板、按钮和搜索框；
- Jade：方块信息提示框；
- 机械动力及其附属：界面加入玻璃面板，并加入工厂场景；
- 饰品栏 (Curios)：物品栏侧边的饰品槽面板；
- 精妙背包、精妙存储：背包和箱子界面、升级栏、侧边设置标签；
- Controlling（按键绑定搜索）：搜索建议下拉框；
- 已测试可正常显示：钠额外选项、Xaero 小地图和世界地图、AppleSkin、传送石碑 (Waystones)；
- 其他模组添加到主菜单的按钮都会保留。

## 注意事项

- 所有功能都可以在模组列表 → 琉璃 → “配置”中单独关闭；显卡吃力时可调低或关闭“模糊质量”，开着光影包时还可以关闭“主菜单场景使用光影”，主菜单的显卡开销会降到几乎为零；
- 首次启动时会把原版“菜单背景模糊度”设为 0，让场景保持清晰。之后在辅助功能设置中手动调回不会再被重置。

## 版权

- 本模组代码以 LGPL-3.0 协议开源。内置的字体、Mojang 标志动画和启动音效不在此列，各自的版权见下；
- 字体使用[鸿蒙黑体 (HarmonyOS Sans)](https://developer.huawei.com/consumer/cn/design/resource/)，版权归华为终端有限公司所有，按 HarmonyOS Sans 字体许可协议使用；
- 玻璃风格的钠视频设置界面基于 FlashyReese 的 [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options)（MIT 协议）；
- Mojang Studios 标志动画的帧图和音效取自 Karasu-Lab 的 [MojangLogoAnimation](https://github.com/Karasu-Lab/MojangLogoAnimation)，并用 [RIFE](https://github.com/hzwer/ECCV2022-RIFE) 从 14 帧/秒补到 114 帧/秒，图像和声音版权归 Mojang 所有；
- 启动音效改编自 C418《Minecraft》的开头；
- 本模组与 Mojang 无关联。

---

<!-- 以下为填写用信息，填到 MC百科模组页面的对应属性字段，不要贴进正文 -->

## 填写用信息（不放正文）

- 支持平台：JAVA版
- 运作方式：NeoForge
- 支持的 MC 版本：1.21.1（NeoForge 21.1）
- 运行环境：客户端需装，服务端无效
- 前置 Mod：无
- 联动的 Mod：钠 (Sodium)、Iris Shaders、JEI、Jade、机械动力 (Create)、饰品栏 (Curios)、精妙背包 (Sophisticated Backpacks)、精妙存储 (Sophisticated Storage)、Controlling
- 开源属性 / 许可证：开源（LGPL-3.0）
