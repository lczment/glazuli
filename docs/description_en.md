# Glazuli

Glazuli (琉璃) is a UI polish mod that redoes the loading screen, title screen and in-game menus.

**This mod was developed with AI assistance**

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
