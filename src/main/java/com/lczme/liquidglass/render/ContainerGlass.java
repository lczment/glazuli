package com.lczme.liquidglass.render;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Glass inventories: a glass panel behind every container screen, its background texture redrawn with the vanilla
 * panel grey made transparent (see {@code container_glass.fsh}), and the hotbar and creative tabs drawn as glass.
 */
public final class ContainerGlass {
    /** Light frosted body: vanilla's container art and dark labels are made for a light grey panel. */
    private static final int LIGHT_FILM = 0xF4F6FA;
    public static final GlassStyle WINDOW = GlassStyle.film(LIGHT_FILM, 0.3F).withCornerRadius(GlassUi.CARD_RADIUS);
    public static final GlassStyle TAB_SELECTED = WINDOW.withCornerRadius(6.0F);
    public static final GlassStyle TAB_UNSELECTED = GlassStyle.film(LIGHT_FILM, 0.14F).withCornerRadius(6.0F);
    private static final GlassStyle HOTBAR = GlassStyle.film(GlassStyle.FILM_COLOR, 0.22F).withCornerRadius(6.0F);
    private static final int HOTBAR_SELECTION = 0x48FFFFFF;
    /** Background-sized blits lose their 3 px bevelled frame; the glass panel has its own edge. */
    private static final float FRAME = 4.0F;
    private static final int MIN_FRAMED_SIZE = 64;

    private static int[] pendingPanel;

    private ContainerGlass() {
    }

    public static boolean isEnabled() {
        return LiquidGlassConfig.SPEC.isLoaded() && LiquidGlassConfig.GLASS_INVENTORY.get();
    }

    /**
     * Called before a container screen draws its background: the window panel is drawn under the first background
     * texture that is actually turned into glass, so a screen retextured by a resource pack keeps the pack's art alone.
     */
    public static void beginBackground(int x, int y, int width, int height) {
        pendingPanel = isEnabled() ? new int[]{x, y, width, height} : null;
    }

    public static void endBackground() {
        pendingPanel = null;
    }

    private static boolean isGlassTexture(ResourceLocation texture) {
        String path = texture.getPath();
        return path.startsWith("textures/gui/container/") || path.equals("textures/gui/recipe_book.png");
    }

    /** Replaces a blit of a container background texture; returns false to let vanilla draw it. */
    public static boolean blit(GuiGraphics guiGraphics, ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
                               float u0, float u1, float v0, float v1) {
        ShaderInstance shader = LiquidGlassShaders.containerGlass();
        if (shader == null || !isEnabled() || !isGlassTexture(texture)) {
            return false;
        }
        int[] panel = pendingPanel;
        pendingPanel = null;
        if (PackOverrides.texture(texture)) {
            return false;
        }
        if (panel != null) {
            GlassRenderer.drawPanel(guiGraphics, panel[0], panel[1], panel[2], panel[3], WINDOW);
        }
        int width = x2 - x1;
        int height = y2 - y1;
        boolean framed = width >= MIN_FRAMED_SIZE && height >= MIN_FRAMED_SIZE;
        if (framed && texture.getPath().equals("textures/gui/recipe_book.png")) {
            GlassRenderer.drawPanel(guiGraphics, x1, y1, width, height, WINDOW);
        }
        guiGraphics.flush();

        shader.safeGetUniform("RectSize").set((float) width, (float) height);
        shader.safeGetUniform("EdgeCut").set(framed ? FRAME : 0.0F);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = guiGraphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(pose, x1, y1, z).setUv(u0, v0).setColor(0.0F, 0.0F, 0.0F, 1.0F);
        builder.addVertex(pose, x1, y2, z).setUv(u0, v1).setColor(0.0F, 1.0F, 0.0F, 1.0F);
        builder.addVertex(pose, x2, y2, z).setUv(u1, v1).setColor(1.0F, 1.0F, 0.0F, 1.0F);
        builder.addVertex(pose, x2, y1, z).setUv(u1, v0).setColor(1.0F, 0.0F, 0.0F, 1.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
        return true;
    }

    /** Glass replacements for HUD and creative-tab sprites; returns false to let vanilla draw the sprite. */
    public static boolean sprite(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (!isEnabled() || !sprite.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) || !isGlassSprite(sprite.getPath())
                || PackOverrides.sprite(sprite)) {
            return false;
        }
        String path = sprite.getPath();
        switch (path) {
            case "hud/hotbar" -> GlassRenderer.drawPanel(guiGraphics, x, y, width, height, HOTBAR);
            // The selection frame overhangs the 20 px slot by 2 px on each side (1 px above the bar).
            case "hud/hotbar_selection" -> GlassUi.roundedFill(guiGraphics, x + 1, y + 1, x + width - 1, y + height - 1, 3, HOTBAR_SELECTION);
            // Offhand sprites are 29 px wide: a 22 px slot plus a gap towards the hotbar.
            case "hud/hotbar_offhand_left" -> GlassRenderer.drawPanel(guiGraphics, x, y + 1, 22, 22, HOTBAR);
            case "hud/hotbar_offhand_right" -> GlassRenderer.drawPanel(guiGraphics, x + width - 22, y + 1, 22, 22, HOTBAR);
            default -> {
                if (!path.startsWith("container/creative_inventory/tab_")) {
                    return false;
                }
                GlassRenderer.drawPanel(guiGraphics, x, y, width, height, path.contains("unselected") ? TAB_UNSELECTED : TAB_SELECTED);
            }
        }
        return true;
    }

    private static boolean isGlassSprite(String path) {
        return path.startsWith("hud/hotbar") || path.startsWith("container/creative_inventory/tab_");
    }
}
