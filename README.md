# 琉璃 (Glazuli)

[English](#glazuli) · [Modrinth](https://modrinth.com/mod/glazuli) · [Releases](https://github.com/lczment/glazuli/releases)

琉璃 (Glazuli) 是一个界面美化模组，重做了加载画面、主菜单和游戏内的各种界面。

**本模组为 AI 辅助开发**

## 下载

- [Modrinth](https://modrinth.com/mod/glazuli)
- [GitHub Releases](https://github.com/lczment/glazuli/releases)

## 要求

- Minecraft 1.21.1
- NeoForge 21.1
- 可选：钠 0.8.13+（低于 0.9）、Iris、JEI、Jade、机械动力、饰品栏、精妙背包/存储、Controlling

## 启动音效

将自己想要的启动音效重命名为 `startup.ogg` 放进 `config/glazuli` 即可替换。

## 主菜单场景

将原版全景图换成了 3D 场景。

- 自带场景：洞穴、繁茂洞穴、深暗之域、冰窟、下界、海底废墟、平原村庄·黄昏、雪原村庄·夜、沙漠村庄、远古城市、试炼密室、堡垒遗迹、末地城；安装机械动力后额外加入一个机器运转的工厂场景；
- 若自定义，可世界中输入 `/menuscene save <名字>`，将周围的方块保存到 `config/glazuli/scenes` 作为新场景，点主菜单的“场景”按钮切换；
- 室外场景的昼夜与电脑时间同步；
- 开启 Iris 光影时，场景将用光影包渲染。

## 玻璃界面

- 面板、按钮、滑块、提示框、聊天栏、通知、Boss 血条和物品栏都换成磨砂玻璃效果；
- 按钮按下时会轻微压扁，切换界面时淡入淡出；
- 修改界面贴图的资源包照常显示原贴图，仅保留动画效果；
- 主菜单右上角的按钮可把菜单换回原版，暂停菜单中另有开关单独控制游戏内界面；
- 字体为鸿蒙黑体，系统装有苹方将使用苹方。将 `.ttf`、`.otf` 或 `.ttc` 文件放进 `config/glazuli/fonts` 可换成其他字体，也可以关闭自定义字体。

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

- 所有功能都可以在模组列表 → 琉璃 → “配置”中单独关闭；显卡吃力时可调低或关闭“模糊质量”，开光影包时亦可关闭“主菜单场景使用光影”。
- 首次启动时会自动将原版“菜单背景模糊度”设为 0。可辅助功能设置中手动调整。

## 版权

- 本模组代码以 LGPL-3.0 协议开源。内置的字体、Mojang 标志动画和启动音效不在此列，各自的版权见下；
- 字体使用[鸿蒙黑体 (HarmonyOS Sans)](https://developer.huawei.com/consumer/cn/design/resource/)，版权归华为终端有限公司所有，按 HarmonyOS Sans 字体许可协议使用；
- 玻璃风格的钠视频设置界面基于 FlashyReese 的 [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options)（MIT 协议）；
- Mojang Studios 标志动画的帧图和音效取自 Karasu-Lab 的 [MojangLogoAnimation](https://github.com/Karasu-Lab/MojangLogoAnimation)，使用 [RIFE](https://github.com/hzwer/ECCV2022-RIFE) 从 14 帧/秒补到 29 帧/秒，图像和声音版权归 Mojang 所有；
- 启动音效改编自 C418《Minecraft》的开头；
- 本模组与 Mojang 无关联。

---

# Glazuli

[中文](#琉璃-glazuli) · [Modrinth](https://modrinth.com/mod/glazuli) · [Releases](https://github.com/lczment/glazuli/releases)

Glazuli (琉璃) is a UI polish mod that redoes the loading screen, title screen and in-game menus.

**This mod was developed with AI assistance**

## Download

- [Modrinth](https://modrinth.com/mod/glazuli)
- [GitHub Releases](https://github.com/lczment/glazuli/releases)

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1
- Optional: Sodium 0.8.13+ (below 0.9), Iris, JEI, Jade, Create, Curios, Sophisticated Backpacks/Storage, Controlling

## Startup sound

Rename your preferred sound to `startup.ogg` and put it in `config/glazuli` to replace the default.

## Menu scenes

Replaces the vanilla panorama with a 3D scene.

- Built-in: Cave, Lush Cave, Deep Dark, Ice Cave, Nether, Ocean Ruins, Plains Village at Dusk, Snowy Village at Night, Desert Village, Ancient City, Trial Chambers, Bastion Remnant, End City; with Create installed, also a running factory scene
- For a custom scene, run `/menuscene save <name>` in a world to save the area around you to `config/glazuli/scenes`, then switch with the Scenes button on the title screen
- Outdoor scenes follow your system clock
- With Iris shaders enabled, scenes are rendered with the shader pack

## Glass UI

- Panels, buttons, sliders, tooltips, chat, toasts, the boss bar and inventories become frosted glass
- Buttons squash slightly when pressed; screens fade between each other
- Resource packs that change GUI textures still show those textures; only the animations are kept
- The top-right button on the title screen restores vanilla menus; the pause menu has a separate toggle for in-game screens
- The font is HarmonyOS Sans, or PingFang if installed. Drop a `.ttf`, `.otf` or `.ttc` into `config/glazuli/fonts` to use your own, or turn custom fonts off

## Compatibility

- Sodium: glass video settings, based on Reese's Sodium Options; requires Sodium 0.8.13 or newer, below 0.9
- Iris Shaders: shader packs in menu scenes; glass buttons on Iris's own screens
- JEI: panels, buttons and search field
- Jade: HUD tooltip
- Create and addons: glass panels on screens, plus the factory scene
- Curios: the side accessory slot panel
- Sophisticated Backpacks and Storage: windows, upgrade column, side tabs
- Controlling (key-bind search): search suggestion dropdown
- Also checked: Sodium Extra, Xaero's Minimap and World Map, AppleSkin, Waystones
- Title-screen buttons added by other mods are kept

## Notes

- Every feature can be turned off in Mods → Glazuli → Config. On weak GPUs, lower or disable Blur Quality; with a shader pack loaded, you can also turn off Shader Pack in Menu Scene.
- On first launch, vanilla Menu Background Blur is set to 0 automatically. You can change it again in Accessibility settings.

## License / credits

- The code is open source under LGPL-3.0. The bundled font, Mojang logo animation and startup sound keep their own terms below.
- Font: [HarmonyOS Sans](https://developer.huawei.com/consumer/cn/design/resource/), © Huawei Device Co., Ltd., under the HarmonyOS Sans Fonts License Agreement.
- Glass Sodium settings based on FlashyReese's [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options) (MIT).
- Mojang Studios logo animation frames and sound from Karasu-Lab's [MojangLogoAnimation](https://github.com/Karasu-Lab/MojangLogoAnimation), interpolated from 14 to 29 fps with [RIFE](https://github.com/hzwer/ECCV2022-RIFE). Artwork and sound belong to Mojang.
- Startup sound based on the opening of "Minecraft" by C418.
- Not affiliated with Mojang.
