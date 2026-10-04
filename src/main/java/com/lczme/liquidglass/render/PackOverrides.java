package com.lczme.liquidglass.render;

import com.lczme.liquidglass.LiquidGlass;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Whether a GUI texture comes from a resource pack the player enabled (UI packs, high contrast, server packs) rather
 * than from vanilla or a mod. Glass replacements step aside for such textures so the pack's art shows; the animations
 * (press squash, hover lift, screen fades) don't depend on the art and keep running.
 */
public final class PackOverrides {
    private static final Map<ResourceLocation, Boolean> SPRITES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Boolean> TEXTURES = new ConcurrentHashMap<>();

    private PackOverrides() {
    }

    public static ResourceManagerReloadListener reloadListener() {
        return resourceManager -> {
            SPRITES.clear();
            TEXTURES.clear();
        };
    }

    /** {@code sprite} is a GUI atlas sprite id, e.g. {@code minecraft:widget/button}. */
    public static boolean sprite(ResourceLocation sprite) {
        return SPRITES.computeIfAbsent(sprite, s -> lookup(s.withPath(path -> "textures/gui/sprites/" + path + ".png")));
    }

    public static boolean texture(ResourceLocation texture) {
        return TEXTURES.computeIfAbsent(texture, PackOverrides::lookup);
    }

    private static boolean lookup(ResourceLocation location) {
        return Minecraft.getInstance().getResourceManager().getResource(location)
                .map(Resource::sourcePackId)
                .map(PackOverrides::isPlayerPack)
                .orElse(false);
    }

    private static boolean isPlayerPack(String packId) {
        return !(packId.equals("vanilla") || packId.equals("mod_resources") || packId.startsWith("mod/")
                || packId.startsWith(LiquidGlass.MODID));
    }
}
