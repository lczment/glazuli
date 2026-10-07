# 琉璃 (Glazuli)

琉璃重做了加载画面、主菜单和游戏界面。纯客户端，进任何服务器都不受影响。

## 启动动画

红底 Mojang 画面加载完后，播放 Mojang Studios 标志动画和音效，镜头穿过标志进入主菜单场景，标题逐个字母落下。按任意键跳过。切资源包、换语言、F3+T 重载时也用这个红底画面，退出游戏时渐黑。

首次进主菜单时会先播一段启动音效，取自 C418《Minecraft》开头四个音。想换掉的话，把自己的 `startup.ogg` 放进 `config/glazuli`。

## 主菜单场景

全景图换成了 3D 场景。打开不同菜单镜头会换位置，进世界时镜头冲进去。

自带场景：洞穴、繁茂洞穴、深暗之域、冰窟、下界、海底废墟、平原村庄·黄昏、雪原村庄·夜、沙漠村庄、远古城市、试炼密室、堡垒遗迹、末地城。装了机械动力还有一个机器运转的工厂。

在世界里输入 `/menuscene save <名字>`，周围的方块会存到 `config/glazuli/scenes` 作为新场景。主菜单的「场景」按钮可以切换。室外场景的昼夜跟电脑时间同步，开了 Iris 光影也会生效。

## 玻璃界面

面板、按钮、滑块、提示框、聊天栏、通知、Boss 血条和物品栏都是磨砂玻璃。按钮按下会压扁，切界面有淡入淡出。改界面贴图的资源包照常显示，只保留动画。

主菜单右上角的按钮可以把菜单换回原版，暂停菜单里另有一个开关管游戏内界面。

字体换成鸿蒙黑体，系统有苹方就用苹方。把 `.ttf`、`.otf` 或 `.ttc` 放进 `config/glazuli/fonts` 可以换字体，也可以关掉。

## 模组适配

- 钠：玻璃风格的视频设置，基于 Reese's Sodium Options
- Iris：主菜单场景支持光影，Iris 界面按钮换成玻璃
- JEI：面板、按钮、搜索框
- Jade：方块信息提示框
- 机械动力及附属：玻璃界面，外加工厂场景

其他模组加在主菜单的按钮都会保留。

## 设置

所有功能都能在模组列表 → 琉璃 → 配置里单独关闭。显卡吃力就把「模糊质量」调低或关掉。首次启动会把原版「菜单背景模糊度」设为 0，之后自己调回去不会再被改。

## 运行环境

- Minecraft 1.21.1
- NeoForge 21.1
- 可选：钠 0.8.13 及以上（低于 0.9）、Iris、JEI、Jade、机械动力

## 致谢

- 字体：[鸿蒙黑体 (HarmonyOS Sans)](https://developer.huawei.com/consumer/cn/design/resource/)，版权归华为终端有限公司所有，按 HarmonyOS Sans 字体许可协议使用。
- 玻璃风格的钠设置界面基于 FlashyReese 的 [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options)（MIT 协议）。
- Mojang Studios 标志动画帧图和音效取自 Karasu-Lab 的 [MojangLogoAnimation](https://github.com/Karasu-Lab/MojangLogoAnimation)，图像和声音版权归 Mojang 所有。
- 启动音效改编自 C418《Minecraft》的开头。
- 本模组与 Mojang 无关联。
