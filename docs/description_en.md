# Glazuli

Glazuli (琉璃, "liuli", is Chinese for glazed glass) reworks the parts of Minecraft you look at before and between playing: the loading screen, the title screen and the menus. It's client-side only, so you can join any server with it installed.

## Startup

The red Mojang screen stays, but when loading finishes the Mojang Studios logo animation plays with its sound. Then the logo turns see-through, the camera flies through it into the menu scene, and the Minecraft title drops in letter by letter. If you've used LabyMod, you'll recognize where the idea came from. Press any key to skip it.

Changing resource packs or language, or pressing F3+T, shows the same red screen instead of the vanilla overlay. Quitting the game fades to black.

The first time you reach the title screen, a short sound plays before the menu music starts. It's a soft take on the first four notes of C418's "Minecraft". You can swap in your own by putting a `startup.ogg` in `config/glazuli`.

## Menu scenes

The panorama is replaced with a real 3D scene, and the camera moves to a new spot each time you open a different menu. Joining a world dives into it.

Built-in scenes: Cave, Lush Cave, Deep Dark, Ice Cave, Nether, Ocean Ruins, Plains Village at Dusk, Snowy Village at Night, Desert Village, Ancient City, Trial Chambers, Bastion Remnant and End City. The villages and other structures are assembled from the game's own structure files. With Create installed you also get a factory with the machines running.

You can make your own: stand somewhere you like in a world and run `/menuscene save <name>`. The area around you is saved to `config/glazuli/scenes` and becomes the menu background. Pick between scenes with the Scenes button on the title screen.

Outdoor scenes follow your computer's clock, so the menu at 7 pm looks like a sunset. If you run an Iris shader pack, the scene is drawn with it.

## Liquid glass UI

Panels, buttons, sliders, tooltips, chat, toasts, the boss bar and inventories are drawn as frosted glass that blurs and bends what's behind it. Buttons squash a little when you press them, and screens fade into each other.

If you only want the moving background, the button in the top-right corner of the title screen switches the menus back to vanilla. The pause menu has its own toggle for the in-game screens, so you can keep glass menus and play with vanilla inventories, or the other way round.

Resource packs that change GUI textures still work. Their art is shown as-is, and only the animations are kept.

The pixel font is replaced with HarmonyOS Sans, or PingFang if your system has it. Drop a `.ttf`, `.otf` or `.ttc` into `config/glazuli/fonts` to use a font of your own, or turn the custom font off.

## Mod support

- **Sodium**: the video settings screen is replaced with a glass version based on Reese's Sodium Options.
- **Iris**: menu scenes are rendered with your shader pack, and Iris's own screens get glass buttons.
- **JEI**: panels, buttons and the search field.
- **Jade**: the tooltip on the HUD.
- **Create** and its addons: their screens get glass panels, and the factory scene is added.

Mods that add their own buttons to the title screen keep them.

## Settings

Everything can be switched off on its own from the mod settings screen (Mods > Glazuli > Config). If the blur is too heavy for your GPU, lower Blur Quality or turn it off.

On first launch, Glazuli sets vanilla's Menu Background Blur to 0 so the scene stays sharp. You can raise it again in Accessibility Settings and it won't be reset.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1
- Optional: Sodium 0.8.13 or newer (below 0.9), Iris, JEI, Jade, Create

## Credits

- Font: [HarmonyOS Sans](https://developer.huawei.com/consumer/cn/design/resource/), © Huawei Device Co., Ltd., used under the HarmonyOS Sans Fonts License Agreement.
- The glass Sodium settings are based on [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options) by FlashyReese (MIT).
- The Mojang Studios logo animation frames and sound were taken from [MojangLogoAnimation](https://github.com/Karasu-Lab/MojangLogoAnimation) by Karasu-Lab. The artwork and sound belong to Mojang.
- The startup sound is based on the opening of "Minecraft" by C418.
- The startup sequence is inspired by LabyMod. Glazuli isn't affiliated with LabyMod or Mojang.
