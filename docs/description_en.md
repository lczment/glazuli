# Glazuli

Glazuli (琉璃) redoes Minecraft's loading screen, title screen and menus. Client-side only, works on any server.

**Note: this mod was developed with AI assistance.**

## Startup

After the red Mojang loading screen, the Mojang Studios logo animation plays with sound. The camera then flies through the logo into the menu scene and the title drops in letter by letter. Any key skips it. Resource reloads (packs, language, F3+T) use the same red screen, and quitting fades to black.

The first time you reach the title screen, a short sound plays before the menu music, based on the first four notes of C418's "Minecraft". Put your own `startup.ogg` in `config/glazuli` to replace it.

## Menu scenes

The panorama is replaced by a 3D scene. The camera moves for each menu and dives in when you join a world.

Built-in: Cave, Lush Cave, Deep Dark, Ice Cave, Nether, Ocean Ruins, Plains Village at Dusk, Snowy Village at Night, Desert Village, Ancient City, Trial Chambers, Bastion Remnant, End City, plus a running factory if Create is installed.

`/menuscene save <name>` saves the area around you to `config/glazuli/scenes` as a new scene. Switch scenes with the Scenes button on the title screen. Outdoor scenes follow your system clock, and Iris shader packs apply to them.

## Glass UI

Panels, buttons, sliders, tooltips, chat, toasts, the boss bar and inventories become frosted glass. Buttons squash when pressed and screens fade between each other. Resource pack GUI textures still show; only the animations are kept.

The top-right button on the title screen turns the menus back to vanilla, and the pause menu has a separate toggle for in-game screens.

The font is HarmonyOS Sans, or PingFang if installed. Drop a `.ttf`, `.otf` or `.ttc` into `config/glazuli/fonts` to use your own, or turn it off.

## Mod support

- Sodium: glass video settings screen, based on Reese's Sodium Options
- Iris: shaders in menu scenes, glass buttons on Iris screens
- JEI: panels, buttons, search field
- Jade: HUD tooltip
- Create and addons: glass screens, factory scene
- Curios: the side slot panel
- Sophisticated Backpacks and Storage: windows, upgrade column, side tabs
- Controlling: key bind search suggestions

Also checked: Sodium Extra, Xaero's Minimap and World Map, AppleSkin, Waystones. Title screen buttons added by other mods are kept.

## Settings

Every feature can be turned off in Mods > Glazuli > Config. On weak GPUs, lower or disable Blur Quality. On first launch, vanilla's Menu Background Blur is set to 0; if you raise it again it stays.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1
- Optional: Sodium 0.8.13 or newer (below 0.9), Iris, JEI, Jade, Create, Curios, Sophisticated Backpacks/Storage, Controlling

## License

The code is open source under LGPL-3.0. The bundled font, the Mojang logo animation and the startup sound keep their own terms, listed below.

## Credits

- Font: [HarmonyOS Sans](https://developer.huawei.com/consumer/cn/design/resource/), © Huawei Device Co., Ltd., used under the HarmonyOS Sans Fonts License Agreement.
- Glass Sodium settings based on [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options) by FlashyReese (MIT).
- Mojang Studios logo animation frames and sound from [MojangLogoAnimation](https://github.com/Karasu-Lab/MojangLogoAnimation) by Karasu-Lab. Artwork and sound belong to Mojang.
- Startup sound based on the opening of "Minecraft" by C418.
- Not affiliated with Mojang.
